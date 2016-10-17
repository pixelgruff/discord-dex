package dex.discord;

import com.google.common.base.Joiner;
import dex.discord.response.PokemonResponder;
import dex.util.ParsingUtils;
import sx.blah.discord.api.events.IListener;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Identify and safely attempt responses to {@link MessageReceivedEvent message events}
 */
public class DexListener implements IListener<MessageReceivedEvent>
{
    // Construct command-matching regex
    private static final Joiner PIPE_JOINER = Joiner.on("|");
    // Matches commands in the format: /command
    // http://regexr.com/3eeuc
    private static final String COMMAND_PATTERN = String.format("^!\\b(%s)",
            PIPE_JOINER.join(DexCommand.values())
    );
    private static final Pattern COMMAND_MATCHER = Pattern.compile(COMMAND_PATTERN);
    private static Predicate<String> IS_COMMAND = message -> COMMAND_MATCHER.matcher(message).matches();

    private final Map<DexCommand, PokemonResponder> responses_;

    public DexListener(final Map<DexCommand, PokemonResponder> responses)
    {
        responses_ = responses;
    }

    @Override
    public void handle(final MessageReceivedEvent event)
    {
        System.out.println("Received message with content: " + event.getMessage().getContent());

        final String sanitizedMessage = ParsingUtils.sanitizeMessageContent(event.getMessage().getContent());
        final Optional<DexCommand> maybeCommand = parseCommand(sanitizedMessage);
        if (maybeCommand.isPresent()) {
            System.out.println("Mapped input to command: " + maybeCommand.get());
            final PokemonResponder responder = responses_.get(maybeCommand.get());
            // Try to run whatever response we've been configured with
            responder.safelyRespond(event);
        } else {
            System.out.println("No command found for input after parsing: " + sanitizedMessage);
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
        if (!IS_COMMAND.test(command)) {
            return Optional.empty();
        }

        // Otherwise return the appropriate command
        try {
            final String commandWord = ParsingUtils.getCommandWord(command);
            return Optional.of(DexCommand.valueOf(commandWord));
        } catch (IllegalArgumentException e) {
            System.err.println(String.format("Could not match command %s to any commands in %s",
                    command, Arrays.toString(DexCommand.values())));
            return Optional.empty();
        }
    }
}
