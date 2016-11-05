package dex.discord.handler;

import dex.discord.DexCommand;
import dex.discord.respond.Responder;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import dex.util.ThrowableUtils;
import me.sargunvohra.lib.pokekotlin.model.Move;
import me.sargunvohra.lib.pokekotlin.model.VerboseEffect;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.Optional;

public class MoveHandler extends DexLookupHandler
{
    public MoveHandler(final DynamicPokeApi client, final NameCache typeIds)
    {
        super(DexCommand.move, client, typeIds);

        Validate.isTrue(client.getSupportedDataTypes().contains(Move.class),
                "Provided PokeAPI client does not support access to Move objects!");
    }

    @Override
    void respond(final MessageReceivedEvent event, final String argument, final Integer id) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        final Responder responder = generateResponder(event, argument, id);
        responder.respond();
    }

    private Responder generateResponder(final MessageReceivedEvent event, final String name, final Integer id)
    {
        final Optional<Move> maybeMove = client_.get(Move.class, id);
        if (!maybeMove.isPresent()) {
            final String response = String.format("I'm sorry.  I couldn't get any information about %s Move #%d)",
                    PrintingUtils.properNoun(name), id);
            return Responder.simpleResponder(event, response);
        }
        final Move move = maybeMove.get();

        final Responder responder = new Responder(event);
        addMoveQuickLook(responder, move);
        addMoveText(responder, move);

        return responder;
    }

    private Responder addMoveQuickLook(final Responder responder, final Move move)
    {
        final String moveName = PrintingUtils.englishName(move.getNames()).getName();
        final StringBuilder replyBuilder = new StringBuilder();

        if (isStatusMove(move)) {
            replyBuilder.append(String.format("%s is a %s-type, %s-class move with %2d%% accuracy and %d PP.",
                    moveName, PrintingUtils.properNoun(move.getType().getName()), move.getDamageClass().getName(),
                    move.getAccuracy(), move.getPp()));
        } else {
            replyBuilder.append(String.format("%s is a %s-type, %s-class move with %d power, %2d%% accuracy and %d PP.",
                    moveName, PrintingUtils.properNoun(move.getType().getName()), move.getDamageClass().getName(),
                    move.getPower(), move.getAccuracy(), move.getPp()));
        }

        responder.addResponse(PrintingUtils.style(replyBuilder.toString(), MessageBuilder.Styles.CODE));
        return responder;
    }

    private Responder addMoveText(final Responder responder, final Move move)
    {
        final VerboseEffect englishEffect = move.getEffectEntries().stream()
                .filter(verboseEffect -> ParsingUtils.isEnglish(verboseEffect.getLanguage()))
                .findFirst()
                .orElseThrow(ThrowableUtils.fail("Could not find an English effect text for move %s!", move));

        // TODO: WTF where's the flavor text m8?  it's in the JSON payload but not in the client API?

        responder.addResponse(PrintingUtils.style(englishEffect.getEffect(), MessageBuilder.Styles.ITALICS));
        return responder;
    }

    private boolean isStatusMove(final Move move)
    {
        return move.getDamageClass().getName().equals("status");
    }
}
