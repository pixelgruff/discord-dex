package dex.pokemon;

import me.sargunvohra.lib.pokekotlin.model.NamedApiResource;
import me.sargunvohra.lib.pokekotlin.model.NamedApiResourceList;
import org.apache.commons.lang3.Validate;

import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Provide a generic interface for iterating over named API resources
 */
class PaginatedNamedResourceIterator implements Iterator<NamedApiResource> {
    // TODO: Some kind of appconfig instead of defaults scattered everywhere
    private static final int BATCH_SIZE = 100;

    private final BiFunction<Integer, Integer, NamedApiResourceList> batchProducer_;

    private Iterator<NamedApiResource> batchIterator_;
    private int batchOffset_ = 0;
    private boolean exhausted_ = false;

    PaginatedNamedResourceIterator(final BiFunction<Integer, Integer, NamedApiResourceList> batchProducer)
    {
        batchProducer_ = batchProducer;
    }

    @Override
    public boolean hasNext() {
        if (batchIterator_ != null && batchIterator_.hasNext()) {
            return true;
        }

        // Short-circuit failure to ensure calling hasNext() doesn't do weird things to our internal state
        if (exhausted_) {
            return false;
        }
        // If we have no current batch to iterate over or the batch is empty, check for new data
        else {
            updateCurrentBatch();
            // We may or may not have new data
            return batchIterator_ != null && batchIterator_.hasNext();
        }
    }

    @Override
    public NamedApiResource next() {
        // We can safely iterate here as long as the user has respected checking hasNext() before calling
        return batchIterator_.next();
    }

    private void updateCurrentBatch()
    {
        Validate.isTrue(!exhausted_, "Cannot update to a new batch for an already-exhausted resource!");

        // Poll the producer for new data
        final NamedApiResourceList resourceList = batchProducer_.apply(batchOffset_, BATCH_SIZE);
        batchOffset_ += BATCH_SIZE;

        // Save an iterable
        batchIterator_ = resourceList.getResults().iterator();
        exhausted_ = resourceList.getNext() == null;
    }
}
