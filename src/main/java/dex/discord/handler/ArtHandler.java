package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.discord.respond.Responder;
import dex.pokemon.NameCache;
import dex.util.DiscordUtils;
import dex.util.PrintingUtils;
import dex.util.SpellingSuggester;
import dex.util.ThrowableUtils;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class ArtHandler extends DexSingleArgumentHandler
{
    private static final Joiner OR_JOINER = Joiner.on(", or ");

    private NameCache speciesIds_;
    private SpellingSuggester speciesNameSuggester_;

    public ArtHandler(final NameCache speciesIds)
    {
        super(DexCommand.art);
        Validate.notNull(speciesIds);
        speciesIds_ = speciesIds;
        speciesNameSuggester_ = new SpellingSuggester(speciesIds.getAllNames());
    }

    @Override
    void respond(MessageReceivedEvent event, String name) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
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

            Responder.simpleResponder(event, noIdResponseBuilder.toString()).respond();
            return;
        }
        final int id = maybeId.get();
        sendArt(event.getMessage().getChannel(), id);
    }

    private void sendArt(final IChannel channel, final int id)
    {
        final String artName = String.format("official-artwork/%d.png", id);
        try (final InputStream art = ClassLoader.getSystemClassLoader().getResourceAsStream(artName)) {
            DiscordUtils.uncheckedSendFile(channel, art);
        } catch (IOException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }
}
