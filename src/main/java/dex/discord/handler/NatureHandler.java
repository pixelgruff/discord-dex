package dex.discord.handler;

import dex.discord.DexCommand;
import dex.discord.respond.TypingStatus;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
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
        // Extract the nature's name from the input
        final String name;
        try {
            name = ParsingUtils.parseFirstArgument(event.getMessage().getContent());
        } catch (Exception e) {
            event.getMessage().reply(HelpHandler.helpResponse(DexCommand.nature));
            return;
        }

        // Construct and send the response
        try (final TypingStatus typing = TypingStatus.start(event.getMessage().getChannel())) {
            // Construct and send the respond
            final String reply = generateReply(name);
            event.getMessage().getChannel().sendMessage(reply);
        }
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
            final NamedApiResource increasedStat = nature.getIncreasedStat();
            final NamedApiResource decreasedStat = nature.getDecreasedStat();

            final String increased = increasedStat != null ? increasedStat.getName() : null;
            final String decreased = decreasedStat != null ? decreasedStat.getName() : null;

            final String diffMessage = PrintingUtils.diff(
                    String.format("%s has the following effects: ", PrintingUtils.properNoun(name)),
                    increased, decreased);
            replyBuilder.append(diffMessage);
        }

        return replyBuilder.toString();
    }
}
