package dex.discord.handler;

import dex.discord.DexCommand;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import me.sargunvohra.lib.pokekotlin.model.Nature;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.util.Optional;

public class NatureHandler extends Handler {

    private final NameCache natureIds_;
    private final DynamicPokeApi client_;

    public NatureHandler(final DynamicPokeApi client, final NameCache natureIds)
    {
        Validate.notNull(client);
        Validate.isTrue(client.getSupportedDataTypes().contains(Nature.class),
                "Provided PokeAPI client does not support access to Nature objects!");
        Validate.notNull(natureIds);

        client_ = client;
        natureIds_ = natureIds;
    }

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException
    {
        // Extract the Pokemon name from the input
        final String name;
        try {
            name = ParsingUtils.parseFirstArgument(event.getMessage().getContent());
        } catch (Exception e) {
            event.getMessage().reply(HelpHandler.helpResponse(DexCommand.nature));
            return;
        }

        // Construct and send the reply
        final String reply = generateReply(name);

        event.getMessage().reply(reply);
    }

    private String generateReply(final String name)
    {
        final Optional<Integer> maybeId = natureIds_.getId(name);
        if (!maybeId.isPresent()) {
            return String.format("I'm sorry.  I couldn't find %s in my list of Pokemon natures.",
                    PrintingUtils.properNoun(name));
        }
        final int id = maybeId.get();

        final Optional<Nature> maybeNature = client_.get(Nature.class, id);
        if (!maybeNature.isPresent()) {
            return String.format("I'm sorry.  I couldn't get any information about %s (Nature #%d)", name, id);
        }
        final Nature nature = maybeNature.get();

        final StringBuilder replyBuilder = new StringBuilder();
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

        return replyBuilder.toString();
    }
}
