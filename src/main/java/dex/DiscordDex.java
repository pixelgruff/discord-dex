package dex;

import com.google.common.collect.ImmutableMap;
import dex.discord.DexCommand;
import dex.discord.DexListener;
import dex.discord.handler.*;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import me.sargunvohra.lib.pokekotlin.client.PokeApi;
import me.sargunvohra.lib.pokekotlin.client.PokeApiClient;
import me.sargunvohra.lib.pokekotlin.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.util.DiscordException;

import java.util.Map;

public class DiscordDex
{
    private static final Logger LOG = LoggerFactory.getLogger(DiscordDex.class);

    // Configure Discord access
    private static final String DEX_BOT_TOKEN = "MjM3NDI1MDE1MzI1MzI3MzYw.CuXe4g.-i6VEkVCeOdeEHJk7FIuUOS2oBc";

    // Configure Pokemon API access
    // TODO: Move cache initialization to somewhere in main()
    private static final PokeApi POKEMON_CLIENT = new PokeApiClient();
    private static final DynamicPokeApi DYNAMIC_CLIENT = DynamicPokeApi.wrap(POKEMON_CLIENT,
            PokemonSpecies.class, Pokemon.class, EvolutionChain.class, Nature.class, Ability.class, Type.class);
    private static final NameCache NATURE_ID_CACHE = NameCache.initializeCache(POKEMON_CLIENT::getNatureList);
    private static final NameCache SPECIES_ID_CACHE = NameCache.initializeCache(POKEMON_CLIENT::getPokemonSpeciesList);
    private static final NameCache ABILITY_ID_CACHE = NameCache.initializeCache(POKEMON_CLIENT::getAbilityList);
    private static final NameCache TYPE_ID_CACHE = NameCache.initializeCache(POKEMON_CLIENT::getTypeList);

    // Wire up bot logic
    private static final Map<DexCommand, Handler> COMMAND_RESPONSES =
            ImmutableMap.<DexCommand, Handler>builder()
                    .put(DexCommand.help, new HelpHandler())
                    .put(DexCommand.nature, new NatureHandler(DYNAMIC_CLIENT, NATURE_ID_CACHE))
                    .put(DexCommand.dex, new DexHandler(DYNAMIC_CLIENT, SPECIES_ID_CACHE))
                    .put(DexCommand.ability, new AbilityHandler(DYNAMIC_CLIENT, ABILITY_ID_CACHE))
                    .put(DexCommand.type, new TypeHandler(DYNAMIC_CLIENT,TYPE_ID_CACHE))
                    .put(DexCommand.delete, new DeleteHandler())
                    .put(DexCommand.ket, new KetHandler())
                    .build();
    private static final DexListener DEX_LISTENER = new DexListener(COMMAND_RESPONSES);

    public static void main(final String[] args) {
        LOG.info("discord-dex is starting up...");
        try {
            final IDiscordClient client = getClient(DEX_BOT_TOKEN, true);
            LOG.info("Got client with token: {}", DEX_BOT_TOKEN);
            client.getDispatcher().registerListener(DEX_LISTENER);
        } catch (DiscordException e) {
            throw new RuntimeException("Couldn't get a Discord client!", e);
        }
    }

    private static IDiscordClient getClient(final String token, final boolean login) throws DiscordException
    {
        // Returns an instance of the Discord client
        ClientBuilder clientBuilder = new ClientBuilder(); // Creates the ClientBuilder instance
        clientBuilder.withToken(token); // Adds the login info to the builder
        if (login) {
            return clientBuilder.login(); // Creates the client instance and logs the client in
        } else {
            return clientBuilder.build(); // Creates the client instance but it doesn't log the client in yet, you would have to call client.login() yourself
        }
    }
}
