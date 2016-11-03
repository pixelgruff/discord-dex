package dex.discord.handler;

import dex.util.IterableUtils;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.util.Arrays;
import java.util.List;

/**
 * Just for fun, when real handlers are still under test
 */
public class CantankerousHandler extends Handler {
    private static final List<String> CANTANKEROUS_REPLIES = Arrays.asList(
            "NO",
            "HUSH",
            "LEAVE ME ALONE",
            "WHY DON'T YOU GO AHEAD AND WRITE THIS FEATURE YOURSELF THERE DR. HURRYUP",
            "UGH",
            "NOH",
            "OH MY GOD"
    );

    @Override
    void respond(MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException
    {
        event.getMessage().getChannel().sendMessage(IterableUtils.randomFrom(CANTANKEROUS_REPLIES));
    }
}
