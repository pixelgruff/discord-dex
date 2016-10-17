package dex.discord.response;

import dex.discord.DexCommand;
import dex.pokemon.PokedexCache;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import me.sargunvohra.lib.pokekotlin.model.Pokemon;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.util.List;
import java.util.Optional;

/**
 * Provide information about a Pokemon's type
 */
public class TypeResponder extends PokemonResponder
{
    private final PokedexCache pokedexCache_;

    public TypeResponder(final PokedexCache pokedexCache) {
        pokedexCache_ = pokedexCache;
    }

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException {
        final String message = event.getMessage().getContent();
        final List<String> arguments = ParsingUtils.parseArguments(message);

        final String reply;
        if (arguments.isEmpty()) {
            reply = HelpResponder.helpResponse(DexCommand.type);
        } else {
            // TODO: name-to-id mapping
            // TODO: caching
            final String pokemonName = arguments.get(0);
            final Optional<Pokemon> maybePokemon = pokedexCache_.getPokemon(pokemonName);

            if (maybePokemon.isPresent()) {
                final Pokemon pokemon = maybePokemon.get();
                reply = PrintingUtils.prettifiedTypes(pokemon.getTypes());
            } else {
                reply = String.format("no Pokedex entry found for Pokemon: %s", pokemonName);
            }
        }

        event.getMessage().reply(reply);
    }
}
