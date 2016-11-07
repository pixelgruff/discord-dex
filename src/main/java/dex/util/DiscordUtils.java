package dex.util;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiscordUtils
{
    private static final List<String> UNHAPPY_REPLIES = Stream.of(
            "AAAAAAAUGH",
            "AAAAAUGH",
            "AAAUGH",
            "AUGH",
            "UGH",
            "NO, NO, NO, NO, NO",
            "NO",
            "NOPE")
            .collect(Collectors.toList());

    public static void uncheckedSendFile(final IChannel channel, final InputStream stream, final String message)
    {
        try {
            channel.sendFile(stream, "guess-who.png", message);
        } catch (MissingPermissionsException | RateLimitException | DiscordException | IOException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }

    public static void uncheckedSendMessage(final IChannel channel, final String message)
    {
        try {
            channel.sendMessage(message);
        } catch (MissingPermissionsException | RateLimitException | DiscordException e) {
            throw ThrowableUtils.toUnchecked(e);
        }
    }

    public static void trySendMessage(final IChannel channel, final String message)
    {
        try {
            channel.sendMessage(message);
        } catch (MissingPermissionsException | RateLimitException | DiscordException e) {
            // Hehehe nope
        }
    }

    public static String getUnhappyReply()
    {
        return IterableUtils.randomFrom(UNHAPPY_REPLIES);
    }
}
