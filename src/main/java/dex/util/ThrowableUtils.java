package dex.util;

import java.util.function.Supplier;

public class ThrowableUtils
{
    public static Supplier<RuntimeException> fail(final String message)
    {
        return () -> new RuntimeException(message);
    }

    public static Supplier<RuntimeException> fail(final String message, final Object... values)
    {
        return () -> new RuntimeException(String.format(message, values));
    }

    public static RuntimeException toUnchecked(final Throwable e)
    {
        return new RuntimeException(e);
    }
}
