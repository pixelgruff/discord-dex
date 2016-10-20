package dex.util;

import com.google.common.base.Joiner;
import me.sargunvohra.lib.pokekotlin.model.PokemonType;

import java.util.List;
import java.util.stream.Collectors;

public class PrintingUtils
{
    private static final Joiner SLASH_JOINER = Joiner.on("/");

    public static String bold(final String text)
    {
        return String.format("**%s**", text);
    }

    public static String properNoun(final String noun)
    {
        return bold(firstUppercase(noun));
    }

    public static String firstUppercase(final String name)
    {
        if (name.length() <= 1) {
            return name.toUpperCase();
        } else {
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    public static String prettifiedTypes(final List<PokemonType> types)
    {
        return SLASH_JOINER.join(types.stream()
                .map(type -> properNoun(type.getType().getName()))
                .collect(Collectors.toList()));
    }
}
