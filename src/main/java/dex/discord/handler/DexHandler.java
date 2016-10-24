package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.EvolutionUtils;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import dex.util.ThrowableUtils;
import me.sargunvohra.lib.pokekotlin.model.ChainLink;
import me.sargunvohra.lib.pokekotlin.model.EvolutionChain;
import me.sargunvohra.lib.pokekotlin.model.Pokemon;
import me.sargunvohra.lib.pokekotlin.model.PokemonSpecies;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DexHandler extends Handler
{
    private static final Joiner OR_JOINER = Joiner.on(" or ");

    private final DynamicPokeApi client_;
    private final NameCache speciesIds_;

    // TODO: Could we abstract this entire pattern away into a 'HandlerThatRequires(List<Class>)' pattern?
    public DexHandler(final DynamicPokeApi client, final NameCache speciesIds)
    {
        Validate.notNull(client);
        Validate.isTrue(client.getSupportedDataTypes().contains(PokemonSpecies.class),
                "Provided PokeAPI client does not support access to PokemonSpecies objects!");
        Validate.isTrue(client.getSupportedDataTypes().contains(Pokemon.class),
                "Provided PokeAPI client does not support access to Pokemon objects!");
        Validate.isTrue(client.getSupportedDataTypes().contains(EvolutionChain.class),
                "Provided PokeAPI client does not support access to EvolutionChain objects!");
        Validate.notNull(speciesIds);

        client_ = client;
        speciesIds_ = speciesIds;
    }

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException
    {
        // Extract the Pokemon name from the input
        final String name;
        try {
            name = ParsingUtils.parseFirstArgument(event.getMessage().getContent());
        } catch (Exception e) {
            event.getMessage().reply(HelpHandler.helpResponse(DexCommand.dex));
            return;
        }

        // Construct and send the reply
        final String reply = generateReply(name);

        event.getMessage().reply(reply);
    }

    private String generateReply(final String name)
    {
        // Construct reply
        // TODO: Some kind of composable pattern to abstract this away from direct String manipulation
        final Optional<Integer> maybeId = speciesIds_.getId(name);
        if (!maybeId.isPresent()) {
            return String.format("I'm sorry.  I couldn't find %s in my list of Pokemon species.",
                    PrintingUtils.properNoun(name));
        }
        final int id = maybeId.get();

        final Optional<PokemonSpecies> maybeSpecies = client_.get(PokemonSpecies.class, id);
        if (!maybeSpecies.isPresent()) {
            return String.format("I'm sorry.  I couldn't get any information about %s (Nature #%d)", name, id);
        }
        final PokemonSpecies species = maybeSpecies.get();

        final StringBuilder replyBuilder = new StringBuilder();

        // Name and type
        final String pokemonName = species.getName();
        final int pokemonId = species.getId();
        final Pokemon pokemon = client_.get(Pokemon.class, pokemonId)
                .orElseThrow(ThrowableUtils.fail("No Pokemon found with name %s, ID %d!", pokemonName, pokemonId));
        replyBuilder.append(String.format("%s is a %s type Pokemon.",
                PrintingUtils.properNoun(pokemonName),
                PrintingUtils.prettifiedTypes(pokemon.getTypes())));

        // Evolves: from, to
        final int evolutionChainId = species.getEvolutionChain().getId();
        final EvolutionChain evolutionChain = client_.get(EvolutionChain.class, evolutionChainId)
                .orElseThrow(ThrowableUtils.fail("No evolution chain found for Pokemon '%s', ID %s", pokemonName, evolutionChainId));
        final Optional<ChainLink> maybePriorEvolution = EvolutionUtils.getPriorEvolution(evolutionChain, pokemonName);
        final List<ChainLink> futureEvolutions = EvolutionUtils.getFutureEvolution(evolutionChain, pokemonName);

        if (maybePriorEvolution.isPresent() && !futureEvolutions.isEmpty()) {
            final String priorEvolutionName = PrintingUtils.properNoun(
                    maybePriorEvolution.get().getSpecies().getName());
            final List<String> futureEvolutionNames = futureEvolutions.stream()
                    .map(evolution -> PrintingUtils.properNoun(evolution.getSpecies().getName()))
                    .collect(Collectors.toList());
            replyBuilder.append(String.format("  It evolves from %s and into %s.",
                    priorEvolutionName, OR_JOINER.join(futureEvolutionNames)));
        } else if (maybePriorEvolution.isPresent() && futureEvolutions.isEmpty()) {
            final String priorEvolutionName = PrintingUtils.properNoun(
                    maybePriorEvolution.get().getSpecies().getName());
            replyBuilder.append(String.format("  It evolves from %s.", priorEvolutionName));
        } else if (!maybePriorEvolution.isPresent() && !futureEvolutions.isEmpty()) {
            final List<String> futureEvolutionNames = futureEvolutions.stream()
                    .map(evolution -> PrintingUtils.properNoun(evolution.getSpecies().getName()))
                    .collect(Collectors.toList());
            replyBuilder.append(String.format("  It evolves into %s.", OR_JOINER.join(futureEvolutionNames)));
        }

        return replyBuilder.toString();
    }
}
