package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.discord.respond.Responder;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.*;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DexHandler extends DexSingleArgumentHandler
{
    private static final Joiner OR_JOINER = Joiner.on(", or ");

    private final DynamicPokeApi client_;
    private final NameCache speciesIds_;
    private final List<BiFunction<Responder, PokemonSpecies, Responder>> responseBuilders_;
    private final SpellingSuggester speciesNameSuggester_;

    public DexHandler(final DynamicPokeApi client, final NameCache speciesIds)
    {
        super(DexCommand.dex);
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
        speciesNameSuggester_ = new SpellingSuggester(speciesIds.getAllNames());
    }

    @Override
    void respond(MessageReceivedEvent event, String argument) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        final Responder responder = generateResponder(event, argument);
        responder.respond();
    }

    private Responder generateResponder(MessageReceivedEvent event, final String name)
    {
        // Construct response
        final Optional<Integer> maybeId = speciesIds_.getId(name);
        if (!maybeId.isPresent()) {
            final StringBuilder noIdResponseBuilder = new StringBuilder();
            noIdResponseBuilder.append(
                    String.format("I'm sorry.  I couldn't find %s in my list of Pokemon species.",
                            PrintingUtils.properNoun(name)));

            // Suggest a name if the lookup failed
            final Collection<String> suggestions = speciesNameSuggester_.suggest(name);
            if (!suggestions.isEmpty()) {
                noIdResponseBuilder.append(
                        String.format("  Did you mean %s?", OR_JOINER.join(
                                suggestions.stream()
                                        .map(PrintingUtils::firstUppercase)
                                        .collect(Collectors.toList()))));
            }

            return Responder.simpleResponder(event, noIdResponseBuilder.toString());
        }
        final int id = maybeId.get();

        final Optional<PokemonSpecies> maybeSpecies = client_.get(PokemonSpecies.class, id);
        if (!maybeSpecies.isPresent()) {
            final String response = String.format("I'm sorry.  I couldn't get any information about %s (Nature #%d)",
                    PrintingUtils.properNoun(name), id);
            return Responder.simpleResponder(event, response);
        }
        final PokemonSpecies species = maybeSpecies.get();

        // TODO: This pattern is really brittle due to the enforced signature of the builder functions
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
        final String name = PrintingUtils.englishName(species.getNames()).getName();
        LOG.info("Adding Pokemon data for {}.", name);

        final int pokemonId = species.getId();
        final Optional<Pokemon> maybePokemon = client_.get(Pokemon.class, pokemonId);
        if (!maybePokemon.isPresent()) {
            final String response = String.format("No Pokemon found with name %s, ID %d.", name, pokemonId);
            LOG.info(response);
            return Responder.simpleResponder(responder.getTrigger(), response);
        }

        final Pokemon pokemon = maybePokemon.get();
        // Add sprites
        responder.addImage(getPokemonSprites(pokemon));

        // TODO: separate 'type' and 'ability' additions
        final String typeMessage = String.format("%s is type %s.", name,
                PrintingUtils.prettifiedTypes(pokemon.getTypes()));
        responder.addResponse(PrintingUtils.style(typeMessage, MessageBuilder.Styles.CODE));

        final List<String> abilityDescriptions = pokemon.getAbilities().stream()
                .map(ability -> ability.isHidden() ?
                        // Add parentheses to hidden abilities
                        String.format("%s (hidden)", PrintingUtils.properNoun(ability.getAbility().getName())) :
                        PrintingUtils.properNoun(ability.getAbility().getName()))
                .collect(Collectors.toList());
        final String abilityMessage = String.format("%s has the ability: %s.",
                name, OR_JOINER.join(abilityDescriptions));
        responder.addResponse(PrintingUtils.style(abilityMessage, MessageBuilder.Styles.CODE));

        return responder;
    }

    // TODO: EvolutionUtils should use IDs, not names
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
            responseBuilder.append(String.format("It evolves from %s.",
                    PrintingUtils.properNoun(maybePriorEvolution.get().getSpecies().getName())));
        } else if (!maybePriorEvolution.isPresent() && !futureEvolutions.isEmpty()) {
            final List<String> futureEvolutionNames = futureEvolutions.stream()
                    .map(evolution -> PrintingUtils.properNoun(evolution.getSpecies().getName()))
                    .collect(Collectors.toList());
            responseBuilder.append(String.format("It evolves into %s.", OR_JOINER.join(futureEvolutionNames)));
        }

        responder.addResponse(PrintingUtils.style(responseBuilder.toString(), MessageBuilder.Styles.CODE));
        return responder;
    }

    private BufferedImage getPokemonSprites(final Pokemon pokemon)
    {
        final List<BufferedImage> sprites = Stream.of(
                pokemon.getSprites().getFrontDefault(),
                pokemon.getSprites().getFrontFemale(),
                pokemon.getSprites().getFrontShiny())
                .filter(url -> url != null)
                .map(url -> {
                    try {
                        final InputStream stream = new URL(url).openStream();
                        return ImageIO.read(stream);
                    } catch (IOException e) {
                        throw ThrowableUtils.toUnchecked(e);
                    }
                })
                .collect(Collectors.toList());

        return ImageUtils.combine(sprites);
    }
}
