package dex.pokemon;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResourceList;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Provide cached access to Pokemon API resources by name
 */
public class NamedCache<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NamedCache.class);

    // Retryer object to retry troublesome API calls
    private final Retryer<T> retryer_;

    // Function to provide the PokeAPI values by ID
    private final Function<Integer, T> valueProducer_;
    // Mapping of names to IDs
    private final ImmutableMap<String, Integer> idMap_;

    private final LoadingCache<Integer, T> cache_ = CacheBuilder.newBuilder()
            .expireAfterAccess(24, TimeUnit.HOURS)
            .build(new CacheLoader<Integer, T>() {
                @Override
                public T load(@NotNull Integer key) throws Exception {
                    // TODO: Move retrying into the client, not the cache itself
                    return retryer_.call(() -> valueProducer_.apply(key));
                }
            });

    private NamedCache(final ImmutableMap<String, Integer> idMap, final Function<Integer, T> valueProducer)
    {
        valueProducer_ = valueProducer;
        idMap_ = idMap;

        // TODO: Some kind of appconfig instead of defaults scattered everywhere
        retryer_ = RetryerBuilder.<T>newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .withWaitStrategy(WaitStrategies.exponentialWait(10, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterDelay(60, TimeUnit.SECONDS))
                .build();
    }

    /**
     * Construct a cache to provide access via resource name to ID-based API calls
     * @param nameProducer  Batched producer of {@link NamedApiResourceList}
     * @param valueProducer Producer of resource to be cached
     * @param <T>           Type of resource to be cached
     * @return  A {@link NamedCache} initialized with a mapping of resource names -> resource IDs
     */
    public static <T> NamedCache<T> initializeCache(
            final BiFunction<Integer, Integer, NamedApiResourceList> nameProducer,
            final Function<Integer, T> valueProducer)
    {
        // Populate the cache with Pokemon entries
        final ImmutableMap.Builder<String, Integer> idMapBuilder = ImmutableMap.builder();

        // TODO: Potential bug if we have a network problem while initializing the cache
        // Recommend finding a way to compose retry logic onto the client itself
        final PaginatedNamedResourceList resourceList = PaginatedNamedResourceList.withBatchedProducer(nameProducer);
        for (final NamedApiResource resource : resourceList) {
            // Translate to lowercase for easier matching
            idMapBuilder.put(resource.getName().toLowerCase(), resource.getId());
        }

        final ImmutableMap<String, Integer> cache = idMapBuilder.build();
        LOG.info("Iterated over all resources ({} total).", cache.size());

        return new NamedCache<>(cache, valueProducer);
    }

    public Optional<T> get(final String name)
    {
        Validate.notNull(name, "Cannot get a resource from a null key!");
        try {
            // Translate to lowercase for successful matching
            final Integer id = idMap_.get(name.toLowerCase());
            if (id == null) {
                return Optional.empty();
            } else {
                return Optional.of(cache_.get(id));
            }
        } catch (ExecutionException e) {
            LOG.error("Encountered exception while retrieving value for key {}!", name, e);
            throw new RuntimeException(e);
        }
    }
}
