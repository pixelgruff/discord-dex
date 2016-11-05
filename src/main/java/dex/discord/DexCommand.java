package dex.discord;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Lower-case enums are used to allow case-sensitive matching against String input
 */
public enum DexCommand
{
    help,
    dex,
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
                if (StringUtils.equalsIgnoreCase(entry.getValue(), name)) {
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
}
