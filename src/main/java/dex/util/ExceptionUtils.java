package dex.util;

import java.util.function.Supplier;

public class ExceptionUtils
{
    public static Supplier<RuntimeException> fail(final String message)
    {
        return () -> new RuntimeException(message);
    }

    public static Supplier<RuntimeException> fail(final String message, final Object... values)
    {
        return () -> new RuntimeException(String.format(message, values));
    }
}
