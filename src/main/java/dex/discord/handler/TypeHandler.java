package dex.discord.handler;

import com.google.common.base.Joiner;
import dex.discord.DexCommand;
import dex.discord.respond.Responder;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.PrintingUtils;
import me.sargunvohra.lib.pokekotlin.model.Name;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.Type;
import me.sargunvohra.lib.pokekotlin.model.TypeRelations;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class TypeHandler extends DexLookupHandler
{
    private static final Joiner AND_JOINER = Joiner.on(", ");

    public TypeHandler(final DynamicPokeApi client, final NameCache typeIds)
    {
        super(DexCommand.type, client, typeIds);

        Validate.isTrue(client.getSupportedDataTypes().contains(Type.class),
                "Provided PokeAPI client does not support access to Type objects!");
    }

    @Override
    void respond(final MessageReceivedEvent event, final String argument, final Integer id) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        final Responder responder = generateResponder(event, argument, id);
        responder.respond();
    }

    private Responder generateResponder(final MessageReceivedEvent event, final String name, final Integer id)
    {
        final Optional<Type> maybeType = client_.get(Type.class, id);
        if (!maybeType.isPresent()) {
            final String response = String.format("I'm sorry.  I couldn't get any information about %s Type #%d)",
                    PrintingUtils.properNoun(name), id);
            return Responder.simpleResponder(event, response);
        }
        final Type type = maybeType.get();

        final Responder responder = new Responder(event);
        addDamageRelationInfo(responder, type);
        addStatisticalInformation(responder, type);

        return responder;
    }

    private Responder addDamageRelationInfo(final Responder responder, final Type type)
    {
        final StringBuilder replyBuilder = new StringBuilder();
        final String typeName = PrintingUtils.englishName(type.getNames()).getName();
        replyBuilder.append(String.format("The %s type has: ", typeName));

        final TypeRelations damageRelations = type.getDamageRelations();

        // Positive effects
        final String positiveFormat = "\n+ %-22s %s";
        if (!damageRelations.getDoubleDamageTo().isEmpty()) {
            replyBuilder.append(String.format(positiveFormat, "double damage against:",
                    prettyPrintNamedResources(damageRelations.getDoubleDamageTo())));
        }
        if (!damageRelations.getHalfDamageFrom().isEmpty()) {
            replyBuilder.append(String.format(positiveFormat, "half damage from:",
                    prettyPrintNamedResources(damageRelations.getHalfDamageFrom())));
        }
        if (!damageRelations.getNoDamageFrom().isEmpty()) {
            replyBuilder.append(String.format(positiveFormat, "no damage from:",
                    prettyPrintNamedResources(damageRelations.getNoDamageFrom())));
        }

        // Negative effects
        final String negativeFormat = "\n- %-22s %s";
        if (!damageRelations.getDoubleDamageFrom().isEmpty()) {
            replyBuilder.append(String.format(negativeFormat, "double damage from:",
                    prettyPrintNamedResources(damageRelations.getDoubleDamageFrom())));
        }
        if (!damageRelations.getHalfDamageTo().isEmpty()) {
            replyBuilder.append(String.format(negativeFormat, "half damage to:",
                    prettyPrintNamedResources(damageRelations.getHalfDamageTo())));
        }
        if (!damageRelations.getNoDamageTo().isEmpty()) {
            replyBuilder.append(String.format(negativeFormat, "no damage to:",
                    prettyPrintNamedResources(damageRelations.getNoDamageTo())));
        }

        final String relationshipInfo = PrintingUtils.code(replyBuilder.toString(), "diff");
        responder.addResponse(relationshipInfo);

        return responder;
    }

    private Responder addStatisticalInformation(final Responder responder, final Type type)
    {
        final StringBuilder replyBuilder = new StringBuilder();

        final String typeName = PrintingUtils.englishName(type.getNames()).getName();
        replyBuilder.append(String.format("There are %d %s-type Pokemon and %d %s-type moves.",
                type.getPokemon().size(), typeName, type.getMoves().size(), typeName));
        if (type.getMoveDamageClass() != null) {
            replyBuilder.append(String.format("\n%s attacks are generally %s-type.",
                    typeName, type.getMoveDamageClass().getName()));
        }

        final String info = PrintingUtils.style(replyBuilder.toString(), MessageBuilder.Styles.CODE);
        responder.addResponse(info);

        return responder;
    }

    private String prettyPrintNamedResources(final List<NamedApiResource> resources)
    {
        return AND_JOINER.join(resources.stream()
                .map(NamedApiResource::getName)
                .map(PrintingUtils::properNoun)
                .collect(Collectors.toList()));
    }
}
