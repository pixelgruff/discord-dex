package dex.discord.respond;

import sx.blah.discord.handle.obj.IChannel;

import java.io.Closeable;
import java.util.concurrent.ForkJoinPool;

public class TypingStatus implements Closeable
{
    private final IChannel channel_;

    private TypingStatus(final IChannel channel)
    {
        channel_ = channel;
    }

    public static TypingStatus start(final IChannel channel)
    {
        setStatusAsync(channel, true);
        return new TypingStatus(channel);
    }

    @Override
    public void close()
    {
        setStatusAsync(channel_, false);
    }

    private static void setStatusAsync(final IChannel channel, final boolean status)
    {
        ForkJoinPool.commonPool().execute(() -> channel.setTypingStatus(status));
    }
}
