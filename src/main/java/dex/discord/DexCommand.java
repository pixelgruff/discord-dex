package dex.discord;

import java.util.Optional;

/**
 * Lower-case enums are used to allow case-sensitive matching against String input
 */
public enum DexCommand
{
    help,
    dex,
    nature;

    public static Optional<DexCommand> optionalValueOf(final String name)
    {
        try {
            return Optional.of(DexCommand.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
