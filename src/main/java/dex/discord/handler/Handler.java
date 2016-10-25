package dex.discord.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;

public abstract class Handler
{
    protected static final Logger LOG = LoggerFactory.getLogger(Handler.class);
    /**
     * Respond, catching any exceptions safely
     * @param event
     */
    public void safelyRespond(final MessageReceivedEvent event)
    {
        try {
            respond(event);
        }
        // Gotta catch 'em all
        catch (Exception e) {
            LOG.error("Was not able to respond to message \"{}\"!", event.getMessage().getContent(), e);
        }
    }

    abstract void respond(final MessageReceivedEvent event)
            throws IOException, MissingPermissionsException, RateLimitException, DiscordException;
}
