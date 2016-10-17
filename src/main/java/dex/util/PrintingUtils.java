package dex.util;

import me.sargunvohra.lib.pokekotlin.model.PokemonType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PrintingUtils
{
    public static String prettifiedTypes(final List<PokemonType> types)
    {
        return Arrays.toString(types.stream()
                .map(type -> type.getType().getName())
                .collect(Collectors.toList())
                .toArray());
    }
}
