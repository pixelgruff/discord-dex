package dex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SnuggleUtils
{
    protected static final Logger LOG = LoggerFactory.getLogger(SnuggleUtils.class);

    private static final String CHEMKAT_DISCORD_ID = "150473801619079168";

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

    public static void respond(final String reply, final MessageReceivedEvent event)
    {
        try {
            final String nickname = NICKNAMES.get(ThreadLocalRandom.current().nextInt(NICKNAMES.size()));
            final String cuteReply = String.format("%s\n(hey, %s, enjoy some consolation cats)", reply, nickname);

            // What even is the internet: http://thecatapi.com/
            final URL url = new URL("http://thecatapi.com/api/images/get?format=src&type=jpg");
            // Extension required for Discord preview
            event.getMessage().getChannel().sendFile(url.openStream(), "cat_tax.jpg", cuteReply);
        } catch (Exception e) {
            LOG.error("Encountered exception while bein' cute in response to '{}'", event.getMessage().getContent(), e);
        }
    }
}
