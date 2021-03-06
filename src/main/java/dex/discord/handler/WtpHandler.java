package dex.discord.handler;

import dex.discord.respond.TypingStatus;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.*;
import me.sargunvohra.lib.pokekotlin.model.PokemonSpecies;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * "Who's that Pokemon?"
 */
public class WtpHandler extends Handler
{
    private static final int TIME_LIMIT_SECONDS = 10;
    private static final int TIME_LIMIT_MAX = 180;

    private final IDiscordClient discordClient_;
    private final DynamicPokeApi pokemonClient_;
    private final NameCache speciesCache_;
    private final SpellingSuggester speciesSuggester_;

    public WtpHandler(final IDiscordClient discordClient, final DynamicPokeApi pokemonClient,
            final NameCache speciesCache)
    {
        Validate.notNull(discordClient, "Cannot create a Who's-That-Pokemon handler without a Discord client!");
        Validate.notNull(pokemonClient, "Cannot create a Who's-That-Pokemon handler without a PokeAPI client!");
        Validate.notNull(speciesCache, "Cannot create a Who's-That-Pokemon handler without a name cache for species!");
        Validate.isTrue(pokemonClient.getSupportedDataTypes().contains(PokemonSpecies.class),
                "Provided PokeAPI client does not support access to PokemonSpecies objects!");

        discordClient_ = discordClient;
        pokemonClient_ = pokemonClient;
        speciesCache_ = speciesCache;
        speciesSuggester_ = new SpellingSuggester(speciesCache.getAllNames());
    }

    @Override
    void respond(MessageReceivedEvent event) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        final IChannel channel = event.getMessage().getChannel();

        final long timeLimit = Long.parseLong(ParsingUtils.getFirstArgument(event.getMessage().getContent())
                .orElse(Long.toString(TIME_LIMIT_SECONDS)));
        if (timeLimit > TIME_LIMIT_MAX) {
            DiscordUtils.uncheckedSendMessage(event.getMessage().getChannel(), String.format(
                    "Don't be ridiculous.  The maximum timeout is %d seconds.", TIME_LIMIT_MAX));
            return;
        }

        try (final TypingStatus typing = TypingStatus.start(channel)){
            final PokemonSpecies randomSpecies = pickRandomSpecies();
            final AtomicBoolean successFlag = new AtomicBoolean(false);
            final long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeLimit);
            sendChallenge(channel, randomSpecies);
            final Predicate<MessageReceivedEvent> listener = gameListenerFor(randomSpecies, successFlag, endTime);
            // Start the game and wait
            discordClient_.getDispatcher().waitFor(listener, timeLimit, TimeUnit.SECONDS);
            LOG.info("Closing a game of 'Who's that Pokemon?' after {} seconds.", timeLimit);

            // Display the answer, if unguessed
            if (!successFlag.get()) {
                final String name = PrintingUtils.englishName(randomSpecies.getNames()).getName();
                sendArt(channel, randomSpecies, String.format("It was %s!", name));
            }
        } catch (InterruptedException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }

    private PokemonSpecies pickRandomSpecies()
    {
        // TODO: This assumes a contiguous range of Pokemon ID#s
        final int totalSpecies = speciesCache_.getAllNames().size();
        final int randomSpeciesId = ThreadLocalRandom.current().nextInt(1, totalSpecies + 1);
        return pokemonClient_.get(PokemonSpecies.class, randomSpeciesId)
                .orElseThrow(ThrowableUtils.fail("Could not obtain a PokemonSpecies for ID #%d!", randomSpeciesId));
    }

    private Predicate<MessageReceivedEvent> gameListenerFor(final PokemonSpecies pokemonSpecies,
            final AtomicBoolean successFlag, final long endTime)
    {
        final String pokemonName = PrintingUtils.englishName(pokemonSpecies.getNames()).getName();

        return (MessageReceivedEvent event) -> {
            // Fast-fail if we're out of time
            if (System.currentTimeMillis() >= endTime) {
                return true;
            }

            final String content = event.getMessage().getContent().trim();
            final IChannel channel = event.getMessage().getChannel();

            // Check name against the configured name
            final boolean guessedCorrectly = pokemonName.equalsIgnoreCase(content);
            if (guessedCorrectly) {
                sendArt(channel, pokemonSpecies, String.format("Yes!  It's %s!", pokemonName));
                successFlag.set(true);
            } else if (!speciesCache_.getAllNames().contains(content)) {
                // Suggest names in case of minor misspellings
                final Optional<String> maybeSuggestion = speciesSuggester_.suggest(content, 3, 1).stream()
                        .findAny();
                if (maybeSuggestion.isPresent()) {
                    DiscordUtils.uncheckedSendMessage(channel, String.format("Did you mean %s?",
                            PrintingUtils.properNoun(maybeSuggestion.get())));
                }
            }
            return guessedCorrectly;
        };
    }

    private static void sendChallenge(final IChannel channel, final PokemonSpecies pokemonSpecies)
    {
        try (final InputStream art = getPokemonOutline(pokemonSpecies)) {
            DiscordUtils.uncheckedSendFile(channel, art, "Who's that Pokemon?");
        } catch (IOException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }

    private void sendArt(final IChannel channel, final PokemonSpecies pokemonSpecies, final String message)
    {
        try (final InputStream art = getPokemonArt(pokemonSpecies)) {
            DiscordUtils.uncheckedSendFile(channel, art, message);
        } catch (IOException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }

    private static InputStream getPokemonArt(final PokemonSpecies pokemonSpecies)
    {
        final int id = pokemonSpecies.getId();
        final String artName = String.format("official-artwork/%d.png", id);
        return ClassLoader.getSystemClassLoader().getResourceAsStream(artName);
    }

    private static InputStream getPokemonOutline(final PokemonSpecies pokemonSpecies)
    {
        try (final InputStream art = getPokemonArt(pokemonSpecies)) {
            // Black out all colored pixels
            final BufferedImage image = ImageIO.read(art);
            ImageUtils.colorImage(image, Color.white);
            return ImageUtils.toPngInputStream(image);

        } catch (IOException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }
}
