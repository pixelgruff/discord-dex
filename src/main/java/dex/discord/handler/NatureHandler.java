package dex.discord.handler;

import dex.pokemon.NamedCache;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import me.sargunvohra.lib.pokekotlin.model.Nature;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.util.List;
import java.util.Optional;

public class NatureHandler extends Handler {

    private final NamedCache<Nature> natureCache_;

    public NatureHandler(final NamedCache<Nature> natureCache)
    {
        Validate.notNull(natureCache);
        natureCache_ = natureCache;
    }

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException
    {
        final String message = event.getMessage().getContent();
        final List<String> arguments = ParsingUtils.parseArguments(message);
        Validate.notEmpty(arguments, "No arguments found for 'nature' command!");

        final String name = arguments.get(0);
        final Optional<Nature> maybeNature = natureCache_.get(name);

        final StringBuilder replyBuilder = new StringBuilder();
        if (maybeNature.isPresent()) {
            final Nature nature = maybeNature.get();


            if (nature.getIncreasedStat() == null && nature.getDecreasedStat() == null)
            {
                replyBuilder.append(String.format("%s has no effect on stats.", PrintingUtils.properNoun(name)));
            } else {
                replyBuilder.append(String.format("%s has the following effects: ```diff", PrintingUtils.properNoun(name)));
                // Construct reply with optional fields
                if (nature.getIncreasedStat() != null) {
                    replyBuilder.append(String.format("\n+ %s", nature.getIncreasedStat().getName()));
                }
                if (nature.getDecreasedStat() != null) {
                    replyBuilder.append(String.format("\n- %s", nature.getDecreasedStat().getName()));
                }
                replyBuilder.append("```");
            }
        } else {
            replyBuilder.append(String.format("no Pokedex entry found for nature: %s", name));
        }

        event.getMessage().reply(replyBuilder.toString());
    }
}
