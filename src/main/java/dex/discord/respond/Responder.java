package dex.discord.respond;

import com.google.common.base.Joiner;
import dex.util.ImageUtils;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

// TODO: Images and text require separate APIs to respond to Discord, but it'd be much nicer to abstract all that away and make more generically composable responses!
public class Responder
{
    private final static Joiner NEWLINE_JOINER = Joiner.on(System.lineSeparator());

    private final MessageReceivedEvent trigger_;

    private final List<String> responses_ = new ArrayList<>();
    private final List<String> imageUrls_ = new ArrayList<>();
    private final List<BufferedImage> images_ = new ArrayList<>();
    private boolean complete_ = false;

    public Responder(final MessageReceivedEvent trigger)
    {
        Validate.notNull(trigger, "Cannot construct replies for a null event!");
        trigger_ = trigger;
    }

    public static Responder simpleResponder(final MessageReceivedEvent trigger, final String message)
    {
        final Responder responder = new Responder(trigger);
        responder.addResponse(message);
        responder.markComplete();
        return responder;
    }

    public void respond() throws IOException, RateLimitException, DiscordException, MissingPermissionsException
    {
        final String response = NEWLINE_JOINER.join(responses_);
        trigger_.getMessage().getChannel().sendMessage(response);

        for (final String imageUrl : imageUrls_) {
            sendImage(imageUrl);
        }

        for (final BufferedImage image : images_) {
            sendImage(image);
        }
    }

    private void sendImage(final String address) throws IOException, RateLimitException, DiscordException, MissingPermissionsException
    {
        final URL url = new URL(address);
        try (final InputStream stream = url.openStream()) {
            trigger_.getMessage().getChannel().sendFile(stream, url.getFile());
        }
    }

    private void sendImage(final BufferedImage image) throws IOException, RateLimitException, DiscordException, MissingPermissionsException
    {
        try (final InputStream stream = ImageUtils.toPngInputStream(image)) {
            trigger_.getMessage().getChannel().sendFile(stream, "image.png");
        }
    }

    public void addResponse(final String response)
    {
        Validate.isTrue(!isComplete(), "Cannot modify a Responder marked as completed!");
        Validate.notNull(response);
        responses_.add(response);
    }

    public void addImageUrl(final String url)
    {
        Validate.isTrue(!isComplete(), "Cannot modify a Responder marked as completed!");
        Validate.notNull(url);
        imageUrls_.add(url);
    }

    public void addImage(final BufferedImage image)
    {
        Validate.isTrue(!isComplete(), "Cannot modify a Responder marked as completed!");
        Validate.notNull(image);
        images_.add(image);
    }

    public MessageReceivedEvent getTrigger()
    {
        return trigger_;
    }

    public void markComplete()
    {
        complete_ = true;
    }

    public boolean isComplete()
    {
        return complete_;
    }
}
