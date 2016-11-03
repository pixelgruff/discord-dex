package dex.pokemon;

import com.github.rholder.retry.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dex.util.ParsingUtils;
import dex.util.IterableUtils;
import dex.util.ThrowableUtils;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResourceList;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Provide access to Pokemon API resource IDs by name
 */
public class NameCache
{
    private static final Logger LOG = LoggerFactory.getLogger(NameCache.class);

    private static final Retryer<NamedApiResourceList> NAMED_RESOURCE_RETRYER = RetryerBuilder.<NamedApiResourceList>newBuilder()
            .retryIfExceptionOfType(IOException.class)
            .withWaitStrategy(WaitStrategies.exponentialWait(10, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterDelay(60, TimeUnit.SECONDS))
            .build();

    // Mapping of names to IDs
    private final ImmutableMap<String, Integer> idMap_;

    private NameCache(final ImmutableMap<String, Integer> idMap)
    {
        idMap_ = idMap;
    }

    /**
     * Construct a cache to provide access via resource name to ID-based API calls
     * @param nameSupplier     Batched producer of {@link NamedApiResourceList}
     * @return  A {@link NameCache} initialized with a mapping of resource names -> resource IDs
     */
    public static NameCache initializeCache(final BiFunction<Integer, Integer, NamedApiResourceList> nameSupplier)
    {
        // Modify the providing function to use retries
        final BiFunction<Integer, Integer, NamedApiResourceList> retryingNameSupplier = attachRetries(nameSupplier);

        // Build up a mapping of resource names -> resource IDs
        final PaginatedNamedResourceList speciesResourceList = PaginatedNamedResourceList.withBatchedProducer(retryingNameSupplier);
        final Map<String, Integer> nameToId = IterableUtils.streamOf(speciesResourceList)
                .collect(Collectors.toMap(namedResource -> ParsingUtils.comparisonFormat(namedResource.getName()), NamedApiResource::getId));
        LOG.info("Built up a mapping of resource names : resource IDs ({} total).", nameToId.size());
        return new NameCache(ImmutableMap.copyOf(nameToId));
    }

    public Optional<Integer> getId(final String name)
    {
        Validate.notNull(name, "Cannot get a resource from a null key!");
        // Translate to lowercase for successful matching
        return Optional.ofNullable(idMap_.get(name.toLowerCase()));
    }

    public ImmutableSet<String> getAllNames()
    {
        return idMap_.keySet();
    }

    private static <T, U> BiFunction<T, U, NamedApiResourceList> attachRetries(final BiFunction<T, U, NamedApiResourceList> namedResourceFunction)
    {
        return (T t, U u) -> {
            try {
                return NAMED_RESOURCE_RETRYER.call(() -> namedResourceFunction.apply(t, u));
            } catch (ExecutionException | RetryException e) {
                LOG.error("Encountered exception while trying to acquire named resource batch!", e);
                throw ThrowableUtils.toUnchecked(e);
            }
        };
    }
}
