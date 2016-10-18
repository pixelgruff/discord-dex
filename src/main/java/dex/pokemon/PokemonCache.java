package dex.pokemon;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import me.sargunvohra.lib.pokekotlin.client.PokeApi;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResourceList;
import me.sargunvohra.lib.pokekotlin.model.Pokemon;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Gotta cache 'em all
 */
public class PokemonCache {
    private static final int BATCH_SIZE = 100;

    private final PokeApi client_;
    private final ImmutableMap<String, Integer> idMap_;
    private final LoadingCache<Integer, Pokemon> cache_ = CacheBuilder.newBuilder()
            .expireAfterAccess(24, TimeUnit.HOURS)
            .build(new CacheLoader<Integer, Pokemon>() {
                @Override
                public Pokemon load(Integer key) throws Exception {
                    return client_.getPokemon(key);
                }
            });

    private PokemonCache(final PokeApi client, final ImmutableMap<String, Integer> idMap)
    {
        client_ = client;
        idMap_ = idMap;
    }

    public static PokemonCache initializeCache(final PokeApi client)
    {
        // Populate the cache with Pokemon entries
        final ImmutableMap.Builder<String, Integer> idMapBuilder = ImmutableMap.builder();

        int offset = 0;
        while (true) {
            final NamedApiResourceList resourceList = client.getPokemonList(offset, BATCH_SIZE);
            for (final NamedApiResource resource : resourceList.getResults()) {
                // Translate to lowercase for successful matching
                idMapBuilder.put(resource.getName().toLowerCase(), resource.getId());
            }
            offset += BATCH_SIZE;

            System.out.println(String.format("Cached %d id-name pairs (%d - %d).", resourceList.getResults().size(),
                    offset, offset + BATCH_SIZE));

            if (resourceList.getNext() == null) {
                break;
            }
        }

        final ImmutableMap<String, Integer> cache = idMapBuilder.build();
        System.out.println(String.format(
                "Reached the end of the resources listed under http://pokeapi.co/api/v2/pokemon (%d total).",
                cache.size()));

        return new PokemonCache(client, cache);
    }

    public Optional<Pokemon> getPokemon(final String name)
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
