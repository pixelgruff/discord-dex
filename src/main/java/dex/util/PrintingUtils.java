package dex.util;

import com.google.common.base.Joiner;
import me.sargunvohra.lib.pokekotlin.model.PokemonType;
import org.apache.commons.lang3.StringUtils;
import sx.blah.discord.util.MessageBuilder;

import java.util.List;
import java.util.stream.Collectors;

public class PrintingUtils
{
    private static final String DIFF_NEW_CONTENT_IDENTIFIER = "+ ";
    private static final String DIFF_OLD_CONTENT_IDENTIFIER = "- ";
    private static final Joiner SLASH_JOINER = Joiner.on("/");

    public static String properNoun(final String noun)
    {
        return firstUppercase(noun);
    }

    public static String firstUppercase(final String name)
    {
        if (name.length() <= 1) {
            return name.toUpperCase();
        } else {
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    public static String style(final String content, final MessageBuilder.Styles... styles)
    {
        String styledContent = content;
        for (MessageBuilder.Styles style: styles) {
            styledContent = String.format("%s%s%s", style.getMarkdown(), styledContent, style.getReverseMarkdown());
        }

        return styledContent;
    }

    // TODO: Enum for supported code types
    public static String code(final String content, final String language)
    {
        final String languageIdentifiedContent = String.format("%s\n%s", language, content);
        return style(languageIdentifiedContent, MessageBuilder.Styles.CODE_WITH_LANG);
    }

    public static String diff(final String newContent, final String oldContent)
    {
        if (StringUtils.isBlank(newContent) && StringUtils.isBlank(oldContent)) {
            return "";
        }

        final StringBuilder diffStringBuilder = new StringBuilder();
        if (!StringUtils.isBlank(newContent)) {
            diffStringBuilder.append(String.format("\n%s%s", DIFF_NEW_CONTENT_IDENTIFIER, newContent));
        }
        if (!StringUtils.isBlank(oldContent)) {
            diffStringBuilder.append(String.format("\n%s%s", DIFF_OLD_CONTENT_IDENTIFIER, oldContent));
        }

        return code(diffStringBuilder.toString(), "diff");
    }

    public static String prettifiedTypes(final List<PokemonType> types)
    {
        return SLASH_JOINER.join(types.stream()
                .map(type -> properNoun(type.getType().getName()))
                .collect(Collectors.toList()));
    }
}
