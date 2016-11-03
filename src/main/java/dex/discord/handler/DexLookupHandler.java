package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.PrintingUtils;
import dex.util.SpellingSuggester;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Template for handlers that take a single argument and look it up against the Pokemon API
 */
public abstract class DexLookupHandler extends DexSingleArgumentHandler
{
    private static final Joiner OR_JOINER = Joiner.on(", or ");

    protected final DynamicPokeApi client_;
    protected final NameCache idCache_;
    protected final SpellingSuggester nameSuggester_;

    DexLookupHandler(final DexCommand command, final DynamicPokeApi client, final NameCache idCache)
    {
        super(command);

        Validate.notNull(client, "Cannot access the PokeAPI with a null client!");
        Validate.notNull(idCache, "Cannot access items via their human-readable names without a name -> ID client!");

        client_ = client;
        idCache_ = idCache;
        nameSuggester_ = new SpellingSuggester(idCache.getAllNames());
    }

    @Override
    void respond(MessageReceivedEvent event, String argument) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        final Optional<Integer> maybeId = idCache_.getId(argument);
        if (!maybeId.isPresent()) {
            final StringBuilder noIdResponseBuilder = new StringBuilder();
            noIdResponseBuilder.append(
                    String.format("I'm sorry, I couldn't find %s.",
                            PrintingUtils.properNoun(argument)));

            // Suggest a name if the lookup failed
            final Collection<String> suggestions = nameSuggester_.suggest(argument);
            if (!suggestions.isEmpty()) {
                noIdResponseBuilder.append(
                        String.format("  Did you mean %s?", OR_JOINER.join(
                                suggestions.stream()
                                        .map(PrintingUtils::firstUppercase)
                                        .collect(Collectors.toList()))));
            }

            event.getMessage().getChannel().sendMessage(noIdResponseBuilder.toString());
            return;
        }

        respond(event, argument, maybeId.get());
    }

    abstract void respond(final MessageReceivedEvent event, final String argument, final Integer id) throws IOException, MissingPermissionsException, RateLimitException, DiscordException;
}
