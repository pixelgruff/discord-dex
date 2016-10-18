package dex.pokemon;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResourceList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // Function to provide the PokeAPI values by ID
    private final Function<Integer, T> valueProducer_;
    // Mapping of names to IDs
    private final ImmutableMap<String, Integer> idMap_;

    private final LoadingCache<Integer, T> cache_ = CacheBuilder.newBuilder()
            .expireAfterAccess(24, TimeUnit.HOURS)
            .build(new CacheLoader<Integer, T>() {
                @Override
                public T load(Integer key) throws Exception {
                    return valueProducer_.apply(key);
                }
            });

    private NamedCache(final ImmutableMap<String, Integer> idMap, final Function<Integer, T> valueProducer)
    {
        valueProducer_ = valueProducer;
        idMap_ = idMap;
    }

    public static <T> NamedCache<T> initializeCache(
            final BiFunction<Integer, Integer, NamedApiResourceList> nameProducer,
            final Function<Integer, T> valueProducer)
    {
        // Populate the cache with Pokemon entries
        final ImmutableMap.Builder<String, Integer> idMapBuilder = ImmutableMap.builder();

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
        try {
            // Translate to lowercase for successful matching
            final Integer id = idMap_.get(name.toLowerCase());
            if (id == null) {
                return Optional.empty();
            } else {
                return Optional.of(cache_.get(id));
            }
        } catch (ExecutionException e) {
            return Optional.empty();
        }
    }
}
