package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.util.ParsingUtils;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Send the 'help' dialog
 */
public class HelpHandler extends Handler
{
    private static final Joiner COMMA_JOINER = Joiner.on(", ");
    private static final Joiner NEWLINE_JOINER = Joiner.on("\n");
    private static final String COMMANDS = COMMA_JOINER.join(Arrays.stream(DexCommand.values())
            .map(Enum::name)
            .collect(Collectors.toList()));
    private static final String STANDARD_HELP = NEWLINE_JOINER.join(
            String.format("usage: `![%s] [arguments]`", COMMANDS),
            "Use `!help [command]` for more details."
    );

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException {
        final String message = event.getMessage().getContent();
        final List<String> arguments = ParsingUtils.parseArguments(message);

        final String reply;
        if (arguments.isEmpty()) {
            reply = STANDARD_HELP;
        } else {
            final Optional<DexCommand> maybeCommand = DexCommand.optionalValueOf(arguments.get(0));
            if (maybeCommand.isPresent()) {
                reply = helpResponse(maybeCommand.get());
            } else {
                reply = String.format("Please provide a help command in the list [%s].", COMMANDS);
            }
        }

        event.getMessage().getChannel().sendMessage(reply);
    }

    /**
     * Provide a helpful handler string for each {@link DexCommand}
     */
    public static String helpResponse(final DexCommand command)
    {
        switch (command) {
            case help:
                return "Please do not ask for help with the help command; it is wasteful, and impolite.";
            case nature:
                return "usage: `!nature [nature name]`\n" +
                        "Example: `!nature Jolly`";
            case dex:
                return "usage: `!dex [pokemon name]`\n" +
                        "Example: `!dex Sneasel`";
            case ability:
                return "usage: `!ability [ability name]`\n" +
                        "Example: `!ability Pickpocket`";
            case type:
                return "usage: `!type [type name]`\n" +
                        "Example: `!type Dark`";
            case delete:
                return "usage: `!delete [limit]`\n" +
                        "Example: `!delete 10`\n" +
                        "would delete my 10 latest messages.";
            case ket:
                return "usage: `!ket`\n" +
                        "For special occasions.";
            default:
                return String.format("No help text defined for %s!  *Someone* has work to do.", command.name());
        }
    }
}
