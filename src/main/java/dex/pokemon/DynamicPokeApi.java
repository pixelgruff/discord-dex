package dex.pokemon;

import com.github.rholder.retry.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dex.util.StreamUtil;
import dex.util.ThrowableUtils;
import me.sargunvohra.lib.pokekotlin.client.PokeApi;
import me.sargunvohra.lib.pokekotlin.model.*;
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
 * Augment the {@link PokeApi} API with retry logic, caching, and query-by-name for {@link PokemonSpecies}
 */
public class DynamicPokeApi
{
    private static final Logger LOG = LoggerFactory.getLogger(DynamicPokeApi.class);

    // Mapping of data types to data accessors
    private final ImmutableMap<Class<?>, Function<Integer, ?>> dataTypeToAccessor_;
    // Cached map to allow lookup by Pokemon name
    private final ImmutableMap<String, Integer> speciesNameToId_;

    private DynamicPokeApi(final ImmutableMap<Class<?>, Function<Integer, ?>> dataTypeToAccessor,
            final ImmutableMap<String, Integer> speciesNameToId)
    {
        dataTypeToAccessor_ = dataTypeToAccessor;
        speciesNameToId_ = speciesNameToId;
    }

    public static DynamicPokeApi wrap(final PokeApi client, Class<?>... supportedDataTypes)
    {
        final Set<Class<?>> supportedDataTypeSet = ImmutableSet.copyOf(supportedDataTypes);

        // Construct a mapping of API data types to the API calls that access them
        final ImmutableMap.Builder<Class<?>, Function<Integer, ?>> dataTypeToAccessorBuilder = ImmutableMap.builder();

        final Class apiClass = client.getClass();
        // Use reflection to acquire, then wrap, functions that return the desired data types
        // This will totally, messily break if the API for the underlying client changes.
        // TODO: Do some smarter inspection to fast-fail in case of emergency
        for (final Method method : apiClass.getMethods()) {
            final Class<?> returnType = method.getReturnType();
            if (supportedDataTypeSet.contains(returnType)) {
                LOG.info("Wrapping access to data of type: {}", returnType.getSimpleName());
                // Accessing methods via reflection adds some performance cost, but not much
                // http://www.jguru.com/faq/view.jsp?EID=246569
                final Function<Integer, ?> accessor = (Integer i) -> {
                    try {
                        return method.invoke(client, i);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        throw ThrowableUtils.toUnchecked(e);
                    }
                };
                dataTypeToAccessorBuilder.put(returnType, accessor);
            }
        }

        final ImmutableMap<Class<?>, Function<Integer, ?>> dataTypeToAccessor = dataTypeToAccessorBuilder.build();
        LOG.info("Built up accessors for the following data types: {}", dataTypeToAccessor.keySet().asList());

        // Build up a mapping of Pokemon names -> PokemonSpecies IDs
        final PaginatedNamedResourceList speciesResourceList = PaginatedNamedResourceList.withBatchedProducer(client::getPokemonSpeciesList);
        final Map<String, Integer> speciesNameToId = StreamUtil.streamOf(speciesResourceList)
                .collect(Collectors.toMap(NamedApiResource::getName, NamedApiResource::getId));
        LOG.info("Built up a mapping of species names : IDs ({} total).", speciesNameToId.size());

        return new DynamicPokeApi(dataTypeToAccessor, ImmutableMap.copyOf(speciesNameToId));
    }

    // TODO: Move named-cache functionality out and rely on NamedCache instead
    public Optional<PokemonSpecies> getPokemonSpecies(final String name)
    {
        final Integer speciesId = speciesNameToId_.get(name.toLowerCase());
        return speciesId != null ? get(PokemonSpecies.class, speciesId) : Optional.empty();
    }

    public <T> Optional<T> get(final Class<T> clazz, final int id)
    {
        final Function<Integer, T> accessor = getAccessorFor(clazz);
        try {
            // Use a raw Retryer to allow reuse of the same Retryer
            final T result = accessor.apply(id);
            return Optional.of(accessor.apply(id));
        } catch (Exception e) {
            return Optional.empty();
        }
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
                        // TODO: Move retrying into the client, not the cache itself
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
