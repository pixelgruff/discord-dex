package dex.util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ParsingUtils
{
    private static final String WHITESPACE_PATTERN = "\\s+"; // Matches arbitrary-length whitespace

    /**
     * Parse a message and return any arguments after the command
     */
    public static List<String> parseArguments(final String message)
    {
        return Arrays.stream(message.split(WHITESPACE_PATTERN))
                .skip(1)
                .map(String::trim)
                // Skip the command
                .collect(Collectors.toList());
    }

    /**
     * Parse a message and return the first word, if any
     */
    public static Optional<String> getFirstWord(final String message)
    {
        final List<String> words = Arrays.stream(message.split(WHITESPACE_PATTERN))
                .map(ParsingUtils::sanitizeMessageContent)
                .collect(Collectors.toList());
        return words.stream().findFirst();
    }

    public static String sanitizeMessageContent(final String unsanitizedMessageContent)
    {
        return unsanitizedMessageContent.trim();
    }

    public static String getCommandWord(final String commandMessage)
    {
        // Remove leading command character
        return commandMessage.substring(1);
    }
}
