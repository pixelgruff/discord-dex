package dex.util;

import com.google.common.base.Joiner;
import me.sargunvohra.lib.pokekotlin.model.PokemonType;
import org.apache.commons.lang3.Validate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PrintingUtils
{
    private static final Joiner COMMA_JOINER = Joiner.on(", ");

    public static String properNoun(final String noun)
    {
        return String.format("**%s**", firstUppercase(noun));
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
        return COMMA_JOINER.join(types.stream()
                .map(type -> type.getType().getName())
                .collect(Collectors.toList()));
    }
}
