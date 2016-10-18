package dex.pokemon;

import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResourceList;

import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Simple Pokemon API {@link Iterable} built around {@link PaginatedNamedResourceIterator paginated iterators}
 */
public class PaginatedNamedResourceList implements Iterable<NamedApiResource> {
    private final Iterator<NamedApiResource> iterator_;

    private PaginatedNamedResourceList(final Iterator<NamedApiResource> iterator)
    {
        iterator_ = iterator;
    }

    public static PaginatedNamedResourceList withBatchedProducer(
            final BiFunction<Integer, Integer, NamedApiResourceList> batchProducer)
    {
        return new PaginatedNamedResourceList(new PaginatedNamedResourceIterator(batchProducer));
    }

    @Override
    public Iterator<NamedApiResource> iterator() {
        return iterator_;
    }
}
