package dex.discord;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lower-case enums are used to allow case-sensitive matching against String input
 */
public enum DexCommand
{
    help,
    dex,
    art,
    nature,
    ability,
    type,
    move,
    wtp,
    delete,
    ket;

    private static Multimap<DexCommand, String> alternateNames_ = ImmutableMultimap.<DexCommand, String>builder()
            .put(wtp, "who's-that-pokemon")
            .put(wtp, "whos-that-pokemon")
            .put(art, "official-art")
            .build();

    public static Optional<DexCommand> optionalValueOf(final String name)
    {
        try {
            return Optional.of(DexCommand.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static Optional<DexCommand> fuzzyMatch(final String name)
    {
        // If the command name matches a command exactly, return it
        final Optional<DexCommand> exactMatch = optionalValueOf(name);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }
        // Otherwise try matching any of the alternate names
        else {
            for (final Map.Entry<DexCommand, String> entry : alternateNames_.entries()) {
                if (entry.getValue().equalsIgnoreCase(name)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public static Collection<String> alternateNames(final DexCommand command)
    {
        return alternateNames_.get(command);
    }

    public static Collection<String> allNames()
    {
        final List<String> standardNames = Arrays.stream(DexCommand.values())
                .map(value -> value.name())
                .collect(Collectors.toList());
        final Collection<String> alternateNames = alternateNames_.values();
        return Stream.concat(standardNames.stream(), alternateNames.stream()).collect(Collectors.toList());
    }
}
