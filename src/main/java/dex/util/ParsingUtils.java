package dex.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ParsingUtils
{
    private static final String WHITESPACE_PATTERN = "\\s+"; // Matches arbitrary-length whitespace

    public static String parseFirstArgument(final String message)
    {
        return parseNthArgument(message, 0);
    }

    public static String parseNthArgument(final String message, final int index)
    {
        Validate.isTrue(index >= 0, "Cannot find the argument at a subzero index!");
        final List<String> arguments = parseArguments(message);
        Validate.inclusiveBetween(1, arguments.size(), index + 1,
                String.format("Insufficient arguments returned from message %s to parse an argument at index %d!",
                        message, index));
        return arguments.get(index);
    }

    /**
     * Parse a message and return any arguments after the command
     */
    public static List<String> parseArguments(final String message)
    {
        Validate.notNull(message, "Cannot parse arguments out of a null message!");
        return Arrays.stream(message.split(WHITESPACE_PATTERN))
                .skip(1)
                .map(String::trim)
                // Skip the command
                .collect(Collectors.toList());
    }

    public static Optional<String> getFirstArgument(final String message)
    {
        if (StringUtils.isBlank(message)) {
            return Optional.empty();
        }

        final List<String> arguments = parseArguments(message);
        if (arguments.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(arguments.get(0));
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

    public static String comparisonFormat(final String s)
    {
        return s.trim().toLowerCase();
    }
}
