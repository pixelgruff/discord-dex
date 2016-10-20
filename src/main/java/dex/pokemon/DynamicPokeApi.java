package dex.pokemon;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import dex.util.StreamUtil;
import me.sargunvohra.lib.pokekotlin.client.PokeApi;
import me.sargunvohra.lib.pokekotlin.model.*;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Augment the {@link PokeApi} API with retry logic, caching, and query-by-name for {@link PokemonSpecies}
 */
public class DynamicPokeApi
{
    private static final Logger LOG = LoggerFactory.getLogger(DynamicPokeApi.class);

    // TODO: Confirm this works, otherwise we need a non-raw Retryer for each cache (probably worth sidestepping)
    // TODO: Caching, retries
    // Generic retryer
    private static final Retryer RETRYER = RetryerBuilder.newBuilder()
            .retryIfExceptionOfType(IOException.class)
            .withWaitStrategy(WaitStrategies.exponentialWait(10, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterDelay(60, TimeUnit.SECONDS))
            .build();

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

    public static DynamicPokeApi wrap(final PokeApi client)
    {
        // Construct a mapping of API data types to the API calls that access them
        final ImmutableMap<Class<?>, Function<Integer, ?>> dataTypeToAccessor = ImmutableMap.<Class<?>, Function<Integer, ?>>builder()
                .put(PokemonSpecies.class, client::getPokemonSpecies)
                .put(Pokemon.class, client::getPokemon)
                .put(Nature.class, client::getNature)
                .put(EvolutionChain.class, client::getEvolutionChain)
                .build();
        LOG.info("Built up accessors for the following data types: {}", dataTypeToAccessor.keySet().asList());

        // Build up a mapping of Pokemon names -> PokemonSpecies IDs
        final PaginatedNamedResourceList speciesResourceList = PaginatedNamedResourceList.withBatchedProducer(client::getPokemonSpeciesList);
        final Map<String, Integer> speciesNameToId = StreamUtil.streamOf(speciesResourceList)
                .collect(Collectors.toMap(NamedApiResource::getName, NamedApiResource::getId));
        LOG.info("Built up a mapping of species names : IDs ({} total).", speciesNameToId.size());

        return new DynamicPokeApi(dataTypeToAccessor, ImmutableMap.copyOf(speciesNameToId));
    }

    public Optional<PokemonSpecies> getPokemonSpecies(final String name)
    {
        final Integer speciesId = speciesNameToId_.get(name.toLowerCase());
        Validate.notNull(speciesId, String.format("%s not found in species names!", name));
        return get(PokemonSpecies.class, speciesId);
    }

    public <T> Optional<T> get(final Class<T> clazz, final int id)
    {
        final Function<Integer, T> accessor = getAccessorFor(clazz);
        try {
            // Use a raw Retryer to allow reuse of the same Retryer
            final T result = (T) RETRYER.call(() -> accessor.apply(id));
            return Optional.of(accessor.apply(id));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private <T> Function<Integer, T> getAccessorFor(final Class<T> clazz)
    {
        final Function<Integer, ?> rawAccessor = dataTypeToAccessor_.get(clazz);
        Validate.notNull(rawAccessor, "No accessor found for data type %s!", clazz.getSimpleName());
        // I don't know a way to dynamically cast a Function type (suspect because it's 'reified'), so we do it live
        // http://www.codeaffine.com/2015/03/04/map-distinct-value-types-using-java-generics/
        return (Function<Integer, T>) rawAccessor;
    }
}
