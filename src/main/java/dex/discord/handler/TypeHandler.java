package dex.discord.handler;

import dex.discord.DexCommand;
import dex.pokemon.PokemonCache;
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
public class TypeHandler extends Handler
{
    private final PokemonCache pokemonCache_;

    public TypeHandler(final PokemonCache pokemonCache) {
        pokemonCache_ = pokemonCache;
    }

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException {
        final String message = event.getMessage().getContent();
        final List<String> arguments = ParsingUtils.parseArguments(message);

        final String reply;
        if (arguments.isEmpty()) {
            reply = HelpHandler.helpResponse(DexCommand.type);
        } else {
            final String pokemonName = arguments.get(0);
            final Optional<Pokemon> maybePokemon = pokemonCache_.getPokemon(pokemonName);

            if (maybePokemon.isPresent()) {
                final Pokemon pokemon = maybePokemon.get();
                reply = String.format("%s has the following types: %s",
                        PrintingUtils.prettifiedName(pokemon.getName()),
                        PrintingUtils.prettifiedTypes(pokemon.getTypes()));
            } else {
                reply = String.format("no Pokedex entry found for Pokemon: %s", pokemonName);
            }
        }

        event.getMessage().reply(reply);
    }
}
