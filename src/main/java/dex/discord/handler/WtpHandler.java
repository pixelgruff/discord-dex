package dex.discord.handler;

import org.apache.commons.lang3.Validate;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;

/**
 * "Who's that Pokemon?"
 */
public class WtpHandler extends Handler
{
    private final IDiscordClient client_;

    public WtpHandler(final IDiscordClient client)
    {
        Validate.notNull(client, "Cannot create a Who's-That-Pokemon handler without a Discord client!");
        client_ = client;
    }

    @Override
    void respond(MessageReceivedEvent event) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {

    }
}
