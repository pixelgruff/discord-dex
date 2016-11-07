package dex.discord.handler;

import dex.discord.DexCommand;
import dex.discord.respond.TypingStatus;
import dex.util.DiscordUtils;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import dex.util.IterableUtils;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handle a single-argument {@link dex.discord.DexCommand command}
 */
public abstract class DexSingleArgumentHandler extends Handler
{
    private final DexCommand command_;

    DexSingleArgumentHandler(final DexCommand command)
    {
        Validate.notNull(command, "Cannot construct a handler for a null command!");
        command_ = command;
    }

    @Override
    void respond(final MessageReceivedEvent event) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        // Extract the nature's name from the input
        final String argument;
        try {
            argument = ParsingUtils.parseFirstArgument(event.getMessage().getContent());
        } catch (Exception e) {
            final String parseFailResponse = String.format("%s\n%s",
                    DiscordUtils.getUnhappyReply(),
                    HelpHandler.helpResponse(command_));
            event.getMessage().getChannel().sendMessage(parseFailResponse);
            return;
        }

        // Hand the argument off to be responded to
        try (final TypingStatus typing = TypingStatus.start(event.getMessage().getChannel())) {
            respond(event, argument);
        }
    }

    abstract void respond(final MessageReceivedEvent event, final String argument) throws IOException, MissingPermissionsException, RateLimitException, DiscordException;
}
