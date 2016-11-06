package dex.util;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SnuggleUtils
{
    protected static final Logger LOG = LoggerFactory.getLogger(SnuggleUtils.class);

    private static final String CHEMKAT_DISCORD_ID = "150473801619079168";
    private static final Retryer<InputStream> KET_RETRYER = RetryerBuilder.<InputStream>newBuilder()
            .withStopStrategy(StopStrategies.stopAfterAttempt(3))
            .retryIfException()
            .build();

    private static final List<String> NICKNAMES = Arrays.asList(
            "cute patoot",
            "fancy cat",
            "queenly elf",
            "splendid maniac",
            "gorgeous girl",
            "lovely lady"
    );

    public static boolean isKat(final MessageReceivedEvent event)
    {
        return event.getMessage().getAuthor().getID().equals(CHEMKAT_DISCORD_ID);
    }

    public static String cuteMissive()
    {
        final String nickname = NICKNAMES.get(ThreadLocalRandom.current().nextInt(NICKNAMES.size()));
        return String.format("(hey, %s, enjoy some consolation cats)", nickname);
    }

    public static void respond(final String reply, final MessageReceivedEvent event)
    {
        try {
            // What even is the internet: http://thecatapi.com/
            try (final InputStream stream = KET_RETRYER.call(
                    () -> new URL("http://thecatapi.com/api/images/get?format=src&type=jpg").openStream())) {
                // Extension required for Discord preview
                event.getMessage().getChannel().sendFile(stream, "cat_tax.jpg", reply);
            }
        } catch (Exception e) {
            LOG.error("Encountered exception while bein' cute in response to '{}'", event.getMessage().getContent(), e);
        }
    }
}
