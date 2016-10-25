package dex.pokemon;

import com.github.rholder.retry.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dex.util.ThrowableUtils;
import me.sargunvohra.lib.pokekotlin.client.PokeApi;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Augment the {@link PokeApi} API with retry logic and caching
 */
public class DynamicPokeApi
{
    private static final Logger LOG = LoggerFactory.getLogger(DynamicPokeApi.class);

    // Mapping of data types to data accessors
    private final ImmutableMap<Class<?>, Function<Integer, ?>> dataTypeToAccessor_;

    private DynamicPokeApi(final ImmutableMap<Class<?>, Function<Integer, ?>> dataTypeToAccessor)
    {
        dataTypeToAccessor_ = dataTypeToAccessor;
    }

    public static DynamicPokeApi wrap(final PokeApi client, Class<?>... supportedDataTypes)
    {
        final Set<Class<?>> supportedDataTypeSet = ImmutableSet.copyOf(supportedDataTypes);
        return wrap(client, supportedDataTypeSet);
    }

    public static DynamicPokeApi wrap(final PokeApi client, Set<Class<?>> supportedDataTypes)
    {
        Validate.notNull(client, "Cannot wrap a null client!");
        Validate.notEmpty(supportedDataTypes, "Cannot generate a useful client that supports no data types!");

        // Construct a mapping of API data types to the API calls that access them
        final Class apiClass = client.getClass();
        final List<Method> accessors = Arrays.stream(apiClass.getMethods())
                .filter((Method m) -> supportedDataTypes.contains(m.getReturnType()))
                .collect(Collectors.toList());
        final Map<Class<?>, Function<Integer, ?>> accessorMap = new HashMap<>(accessors.size());

        // Use reflection to acquire, then wrap, functions that return the desired data types
        // This will totally, messily break if the API for the underlying client changes.
        // TODO: Do some smarter inspection to fast-fail in case of emergency
        for (final Method method : accessors) {
            // Identify any duplicate methods for obtaining the same data
            final Class<?> returnType = method.getReturnType();
            LOG.info("Wrapping access to data of type: {}", returnType.getSimpleName());
            final Function<Integer, ?> wrappedAccessor = wrapAccessorMethod(client, method);
            final Function<Integer, ?> previousAccessor = accessorMap.put(returnType, wrappedAccessor);

            Validate.isTrue(previousAccessor == null, "%s exposes an API that has multiple accessors for data of type: %s!",
                    apiClass.getSimpleName(), returnType.getSimpleName());
        }

        final ImmutableMap<Class<?>, Function<Integer, ?>> immutableAccessorMap = ImmutableMap.copyOf(accessorMap);
        LOG.info("Wrapped accessors for the following data types: {}", immutableAccessorMap.keySet().asList());

        return new DynamicPokeApi(immutableAccessorMap);
    }

    public <T> Optional<T> get(final Class<T> clazz, final int id)
    {
        final Function<Integer, T> accessor = getAccessorFor(clazz);
        try {
            final T result = accessor.apply(id);
            return Optional.of(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Set<Class<?>> getSupportedDataTypes()
    {
        return dataTypeToAccessor_.keySet();
    }

    private <T> Function<Integer, T> getAccessorFor(final Class<T> clazz)
    {
        try {
            final Function<Integer, ?> rawAccessor = dataTypeToAccessor_.get(clazz);
            Validate.notNull(rawAccessor, "No accessor found for data type %s!", clazz.getSimpleName());
            // I don't know a way to dynamically cast a Function type (suspect because it's 'reified'), so we do it live
            // http://www.codeaffine.com/2015/03/04/map-distinct-value-types-using-java-generics/
            return (Function<Integer, T>) rawAccessor;
        } catch (Exception e) {
            LOG.error("Encountered exception getting the accessor for data of type {}!", clazz.getSimpleName(), e);
            throw e;
        }
    }

    private static <T> Function<T, ?> wrapAccessorMethod(final Object parent, final Method method)
    {
        // Accessing methods via reflection adds some performance cost, but not much
        // http://www.jguru.com/faq/view.jsp?EID=246569
        final Function<T, ?> accessor = (T t) -> {
            try {
                return method.invoke(parent, t);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw ThrowableUtils.toUnchecked(String.format("Not able to invoke dynamically-wrapped method %s!",
                        method.getName()), e);
            }
        };
        return wrapAccessor(accessor);
    }

    /**
     * Wrap an accessing function in caching and retries
     */
    private static <T, R> Function<T, R> wrapAccessor(final Function<T, R> accessor)
    {
        final Function<T, R> retryingAccessor = attachDefaultRetries(accessor,
                Arrays.asList(IOException.class, RuntimeException.class));
        return attachDefaultCache(retryingAccessor);
    }

    /**
     * Decorate a function such that its results are accessed through a {@link com.google.common.cache.LoadingCache}
     */
    private static <T, R> Function<T, R> attachDefaultCache(final Function<T, R> function)
    {
        final LoadingCache<T, R> cache = CacheBuilder.newBuilder()
                .expireAfterAccess(24, TimeUnit.HOURS)
                .build(new CacheLoader<T, R>()
                {
                    @Override
                    public R load(@NotNull T key) throws Exception
                    {
                        return function.apply(key);
                    }
                });

        return (T input) -> {
            try {
                return cache.get(input);
            } catch (ExecutionException e) {
                throw ThrowableUtils.toUnchecked(e);
            }
        };
    }

    /**
     * Decorate a function such that its results are accessed through a {@link Retryer}
     */
    private static <T, R> Function<T, R> attachDefaultRetries(final Function<T, R> function,
            final List<Class<? extends Throwable>> retryableExceptionTypes)
    {
        final RetryerBuilder<R> retryerBuilder = RetryerBuilder.<R>newBuilder()
                .withWaitStrategy(WaitStrategies.exponentialWait(10, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterDelay(60, TimeUnit.SECONDS));
        // Mark the provided exception types as eligible for retries
        retryableExceptionTypes.forEach(retryerBuilder::retryIfExceptionOfType);
        final Retryer<R> retryer = retryerBuilder.build();

        return (T input) -> {
            try {
                // Use a raw Retryer to allow reuse of the same Retryer
                return retryer.call(() -> function.apply(input));
            } catch (ExecutionException | RetryException e) {
                throw ThrowableUtils.toUnchecked(e);
            }
        };
    }
}
