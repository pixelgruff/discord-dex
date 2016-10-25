package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.discord.respond.Responder;
import dex.discord.respond.TypingStatus;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.EvolutionUtils;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import me.sargunvohra.lib.pokekotlin.model.ChainLink;
import me.sargunvohra.lib.pokekotlin.model.EvolutionChain;
import me.sargunvohra.lib.pokekotlin.model.Pokemon;
import me.sargunvohra.lib.pokekotlin.model.PokemonSpecies;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class DexHandler extends Handler
{
    private static final Joiner OR_JOINER = Joiner.on(" or ");

    private final DynamicPokeApi client_;
    private final NameCache speciesIds_;
    private final List<BiFunction<Responder, PokemonSpecies, Responder>> responseBuilders_;

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
        responseBuilders_ = Arrays.asList(this::addPokemonData, this::addEvolutionData);
    }

    @Override
    void respond(final MessageReceivedEvent event)
            throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        // Extract the Pokemon name from the input
        final String name;
        try {
            name = ParsingUtils.parseFirstArgument(event.getMessage().getContent());
        } catch (Exception e) {
            event.getMessage().reply(HelpHandler.helpResponse(DexCommand.dex));
            return;
        }

        // Construct and send the response
        try (final TypingStatus typing = TypingStatus.start(event.getMessage().getChannel())) {
            final Responder responder = generateResponder(event, name);
            responder.send();
        }
    }

    private Responder generateResponder(MessageReceivedEvent event, final String name)
    {
    // Construct response
        final Optional<Integer> maybeId = speciesIds_.getId(name);
        if (!maybeId.isPresent()) {
            final String response = String.format("I'm sorry.  I couldn't find %s in my list of Pokemon species.",
                    PrintingUtils.properNoun(name));
            return Responder.simpleResponder(event, response);
        }
        final int id = maybeId.get();

        final Optional<PokemonSpecies> maybeSpecies = client_.get(PokemonSpecies.class, id);
        if (!maybeSpecies.isPresent()) {
            final String response = String.format("I'm sorry.  I couldn't get any information about %s (Nature #%d)",
                    PrintingUtils.properNoun(name), id);
            return Responder.simpleResponder(event, response);
        }
        final PokemonSpecies species = maybeSpecies.get();

        Responder responder = new Responder(event);
        for (final BiFunction<Responder, PokemonSpecies, Responder> builder : responseBuilders_) {
            responder = builder.apply(responder, species);
            // Short-circuit return if we complete early
            if (responder.isComplete()) {
                return responder;
            }
        }

        return responder;
    }

    private Responder addPokemonData(final Responder responder, final PokemonSpecies species)
    {
        final String name = species.getName();
        LOG.info("Adding Pokemon data for {}.", name);

        final int pokemonId = species.getId();
        final Optional<Pokemon> maybePokemon = client_.get(Pokemon.class, pokemonId);
        if (!maybePokemon.isPresent()) {
            final String response = String.format("No Pokemon found with name %s, ID %d.", name, pokemonId);
            LOG.info(response);
            return Responder.simpleResponder(responder.getTrigger(), response);
        }

        final Pokemon pokemon = maybePokemon.get();
        final String typeMessage = String.format("%s is a %s type Pokemon.", PrintingUtils.properNoun(name),
                PrintingUtils.prettifiedTypes(pokemon.getTypes()));
        responder.addResponse(PrintingUtils.style(typeMessage, MessageBuilder.Styles.CODE));
        responder.addImageUrl(pokemon.getSprites().getFrontDefault());

        return responder;
    }

    private Responder addEvolutionData(final Responder responder, final PokemonSpecies species)
    {
        final String name = species.getName();
        LOG.info("Adding evolution data for {}.", name);

        final int chainId = species.getEvolutionChain().getId();
        final Optional<EvolutionChain> maybeEvolutionChain = client_.get(EvolutionChain.class, chainId);
        if (!maybeEvolutionChain.isPresent()) {
            final String response = String.format("No evolution chain found for Pokemon '%s', ID %s", name, chainId);
            LOG.info(response);
            return Responder.simpleResponder(responder.getTrigger(), response);
        }

        final EvolutionChain evolutionChain = maybeEvolutionChain.get();
        final Optional<ChainLink> maybePriorEvolution = EvolutionUtils.getPriorEvolution(evolutionChain, name);
        final List<ChainLink> futureEvolutions = EvolutionUtils.getFutureEvolution(evolutionChain, name);

        // Do not add any output for Pokemon with no evolutions
        if (!maybePriorEvolution.isPresent() && futureEvolutions.isEmpty()) {
            return responder;
        }

        final StringBuilder responseBuilder = new StringBuilder();
        if (maybePriorEvolution.isPresent() && !futureEvolutions.isEmpty()) {
            final String priorEvolutionName = PrintingUtils.properNoun(
                    maybePriorEvolution.get().getSpecies().getName());
            final List<String> futureEvolutionNames = futureEvolutions.stream()
                    .map(evolution -> PrintingUtils.properNoun(evolution.getSpecies().getName()))
                    .collect(Collectors.toList());
            responseBuilder.append(String.format("It evolves from %s and into %s.",
                    priorEvolutionName, OR_JOINER.join(futureEvolutionNames)));
        } else if (maybePriorEvolution.isPresent() && futureEvolutions.isEmpty()) {
            final String priorEvolutionName = maybePriorEvolution.get().getSpecies().getName();
            responseBuilder.append(String.format("It evolves from %s.", priorEvolutionName));
        } else if (!maybePriorEvolution.isPresent() && !futureEvolutions.isEmpty()) {
            final List<String> futureEvolutionNames = futureEvolutions.stream()
                    .map(evolution -> PrintingUtils.properNoun(evolution.getSpecies().getName()))
                    .collect(Collectors.toList());
            responseBuilder.append(String.format("It evolves into %s.", OR_JOINER.join(futureEvolutionNames)));
        }

        responder.addResponse(PrintingUtils.style(responseBuilder.toString(), MessageBuilder.Styles.CODE));
        return responder;
    }
}
