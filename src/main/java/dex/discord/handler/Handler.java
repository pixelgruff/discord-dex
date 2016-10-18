package dex.discord.handler;

import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

public abstract class Handler
{
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
            // TODO: Fix Log4J to get better error reporting
            System.err.println(String.format("Was not able to respond to message \"%s\"; experienced exception %s!",
                    event.getMessage().getContent(), e.getMessage()));
        }
    }

    abstract void respond(final MessageReceivedEvent event) throws MissingPermissionsException, RateLimitException, DiscordException;
}
