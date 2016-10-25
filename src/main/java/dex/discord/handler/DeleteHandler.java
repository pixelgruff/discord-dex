package dex.discord.handler;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import dex.util.ParsingUtils;
import dex.util.ThrowableUtils;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DeleteHandler extends Handler
{
    private static final String DEX_BOT_DISCORD_ID = "237425015325327360";
    private static final int DEFAULT_DELETE_LIMIT = 1;

    private static final Retryer<Void> RETRYER = RetryerBuilder.<Void>newBuilder()
            .withWaitStrategy(WaitStrategies.exponentialWait(10, TimeUnit.SECONDS))
            .withStopStrategy(StopStrategies.stopAfterDelay(60, TimeUnit.SECONDS))
            .retryIfException()
            .build();

    @Override
    void respond(MessageReceivedEvent event) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        final IChannel channel = event.getMessage().getChannel();

        // Acquire limit
        Optional<String> maybeLimit = ParsingUtils.getFirstArgument(event.getMessage().getContent());
        final int limit = maybeLimit.isPresent() ? Integer.parseInt(maybeLimit.get()) : DEFAULT_DELETE_LIMIT;

        final List<IMessage> channelMessages = channel.getMessages();
        final AtomicInteger deleteCount = new AtomicInteger(0);
        try {
            channelMessages.stream()
                    // Sort by timestamp to operate on only the most recent messages
                    // Note the argument swap to achieve 'latest to earliest' ordering
                    .sorted((IMessage lhs, IMessage rhs) -> rhs.getTimestamp().compareTo(lhs.getTimestamp()))
                    .filter(message -> message.getAuthor().getID().equals(DEX_BOT_DISCORD_ID))
                    .limit(limit)
                    .forEach(message -> {
                        tryDelete(message);
                        deleteCount.incrementAndGet();
                    });
        } catch (Exception e) {
            LOG.error("Encountered exception while trying to delete {} messages!", limit, e);
        }

        // Let the channel users know what you did
        final String status = String.format("Deleted %d messages.", deleteCount.get());
        channel.sendMessage(status);
    }

    private static void tryDelete(final IMessage message)
    {
        try {
            RETRYER.call(() -> {
                message.delete();
                return null;
            });

        } catch (Exception e) {
            throw ThrowableUtils.toUnchecked(
                    String.format("Encountered exception while deleting message %s!", message.getContent()), e);
        }
    }
}
