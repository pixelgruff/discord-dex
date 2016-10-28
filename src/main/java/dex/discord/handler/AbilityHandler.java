package dex.discord.handler;

import dex.discord.DexCommand;
import dex.discord.respond.TypingStatus;
import dex.pokemon.DynamicPokeApi;
import dex.pokemon.NameCache;
import dex.util.ParsingUtils;
import dex.util.PrintingUtils;
import dex.util.ThrowableUtils;
import me.sargunvohra.lib.pokekotlin.model.Ability;
import me.sargunvohra.lib.pokekotlin.model.VerboseEffect;
import org.apache.commons.lang3.Validate;
import sx.blah.discord.handle.impl.events.MessageReceivedEvent;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.IOException;
import java.util.Optional;

// TODO: Consolidate this functionality into "NameLookupHandler"?
// TODO: Provide more convenient:
/*
 * single-argument parsing & validation
 * association of a handler with a command word
 * spelling suggestions
 */
public class AbilityHandler extends Handler
{
    private final NameCache abilityIds_;
    private final DynamicPokeApi client_;

    public AbilityHandler(final DynamicPokeApi client, final NameCache abilityIds)
    {
        Validate.notNull(client);
        Validate.isTrue(client.getSupportedDataTypes().contains(Ability.class),
                "Provided PokeAPI client does not support access to Ability objects!");
        Validate.notNull(abilityIds);

        client_ = client;
        abilityIds_ = abilityIds;
    }

    @Override
    void respond(MessageReceivedEvent event) throws IOException, MissingPermissionsException, RateLimitException, DiscordException
    {
        // Extract the ability's name from the input
        final String name;
        try {
            name = ParsingUtils.parseFirstArgument(event.getMessage().getContent());
        } catch (Exception e) {
            event.getMessage().reply(HelpHandler.helpResponse(DexCommand.ability));
            return;
        }

        // Construct and send the response
        try (final TypingStatus typing = TypingStatus.start(event.getMessage().getChannel())) {
            // Construct and send the respond
            final String reply = generateReply(name);
            event.getMessage().getChannel().sendMessage(reply);
        }
    }

    private String generateReply(final String name)
    {
        final Optional<Integer> maybeId = abilityIds_.getId(name);
        if (!maybeId.isPresent()) {
            return String.format("I'm sorry.  I couldn't find %s in my list of Pokemon abilities.",
                    PrintingUtils.properNoun(name));
        }
        final int id = maybeId.get();

        final Optional<Ability> maybeAbility = client_.get(Ability.class, id);
        if (!maybeAbility.isPresent()) {
            return String.format("I'm sorry.  I couldn't get any information about %s (Ability #%d)", name, id);
        }
        final Ability ability = maybeAbility.get();

        final StringBuilder replyBuilder = new StringBuilder();

        final VerboseEffect englishEffect = ability.getEffectEntries().stream()
                .filter(effect -> ParsingUtils.isEnglish(effect.getLanguage()))
                .findFirst()
                .orElseThrow(ThrowableUtils.fail(
                        "Could not find an English description for ability %s!", ability.getName()));
        replyBuilder.append(String.format("%s: %s\n%s",
                PrintingUtils.properNoun(ability.getName()),
                englishEffect.getShortEffect(), englishEffect.getEffect()));

        return PrintingUtils.style(replyBuilder.toString(), MessageBuilder.Styles.CODE);
    }
}
