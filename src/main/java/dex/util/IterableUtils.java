package dex.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * http://stackoverflow.com/questions/23932061/convert-iterable-to-stream-using-java-8-jdk
 */
public class IterableUtils
{
    /**
     * Converts Iterable to stream
     */
    public static <T> Stream<T> streamOf(final Iterable<T> iterable)
    {
        return toStream(iterable, false);
    }

    /**
     * Converts Iterable to parallel stream
     */
    public static <T> Stream<T> parallelStreamOf(final Iterable<T> iterable)
    {
        return toStream(iterable, true);
    }

    public static <T> T randomFrom(final List<T> list)
    {
        final int index = ThreadLocalRandom.current().nextInt(list.size());
        return list.get(index);
    }

    private static <T> Stream<T> toStream(final Iterable<T> iterable, final boolean isParallel)
    {
        return StreamSupport.stream(iterable.spliterator(), isParallel);
    }
}
