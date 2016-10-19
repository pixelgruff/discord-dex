package dex.discord;

import com.google.common.base.Joiner;
import dex.discord.handler.Handler;
import dex.util.ParsingUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.events.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identify and safely attempt responses to {@link MessageReceivedEvent message events}
 */
public class DexListener implements IListener<MessageReceivedEvent>
{
    private static final Logger LOG = LoggerFactory.getLogger(DexListener.class);

    // Construct command-matching regex
    private static final Joiner PIPE_JOINER = Joiner.on("|");
    // Matches commands in the format: /command
    // http://regexr.com/3eeuc
    private static final String COMMAND_PATTERN = String.format("^!\\b(%s)",
            PIPE_JOINER.join(DexCommand.values())
    );
    private static final Pattern COMMAND_MATCHER = Pattern.compile(COMMAND_PATTERN);

    private final Map<DexCommand, Handler> responses_;

    public DexListener(final Map<DexCommand, Handler> responses)
    {
        responses_ = responses;
    }

    @Override
    public void handle(final MessageReceivedEvent event)
    {
        final String originalMessage = event.getMessage().getContent();
        final String sanitizedMessage = ParsingUtils.sanitizeMessageContent(originalMessage);
        final Optional<DexCommand> maybeCommand = parseCommand(sanitizedMessage);
        if (maybeCommand.isPresent()) {
            final DexCommand command = maybeCommand.get();
            LOG.info("Mapped input {} to command: {}", originalMessage, command);

            // Try to run whatever handler we've been configured with
            final Handler responder = responses_.get(command);
            Validate.notNull(responder, String.format("Could not find a handler for command %s!", command));
            responder.safelyRespond(event);
        }
    }

    /**
     * Parse a message event and return the command it represents, if any
     */
    private static Optional<DexCommand> parseCommand(final String message)
    {
        // Parse out the command word
        final Optional<String> maybeCommand = ParsingUtils.getFirstWord(message);
        // Short-circuit fail on messages with no command (e.g., "/" alone)
        if (!maybeCommand.isPresent()) {
            return Optional.empty();
        }

        final String command = maybeCommand.get();
        // Short-circuit fail on messages that aren't commands
        if (!isCommand(command)) {
            return Optional.empty();
        }

        // Otherwise return the appropriate command
        try {
            final String commandWord = ParsingUtils.getCommandWord(command);
            return Optional.of(DexCommand.valueOf(commandWord));
        } catch (IllegalArgumentException e) {
            LOG.info("Could not match command {} to any commands in {}", command, Arrays.toString(DexCommand.values()));
            return Optional.empty();
        }
    }

    private static boolean isCommand(final String message)
    {
        return COMMAND_MATCHER.matcher(message).matches();
    }
}
