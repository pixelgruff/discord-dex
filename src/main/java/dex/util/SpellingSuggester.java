package dex.util;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.Collection;
import java.util.Collections;

/**
 * Match input words to the closest word in a given dictionary, where 'closest' is defined by 'edit distance'
 * @see <a href="https://en.wikipedia.org/wiki/Edit_distance">Edit distance</a>
 */
public class SpellingSuggester
{
    private static final int DEFAULT_SUGGESTION_LIMIT = 10;

    private final ImmutableSet<String> dictionary_;

    public SpellingSuggester(final Collection<String> dictionary)
    {
        Validate.notEmpty(dictionary, "Cannot suggest spellings with an empty dictionary!");
        dictionary_ = ImmutableSet.copyOf(dictionary);
    }

    public Collection<String> suggest(final String input)
    {
        return suggest(input, input.length() - 1, DEFAULT_SUGGESTION_LIMIT);
    }

    public Collection<String> suggest(final String input, final int maximumDistance, final int maximumResponses)
    {
        // Learned me a thing: http://stackoverflow.com/questions/23003754/
        final Multimap<Integer, String> distances = dictionary_.stream()
                .collect(ImmutableMultimap::<Integer, String>builder,
                        (builder, word) -> {
                            final int distance = StringUtils.getLevenshteinDistance(word, input);
                            if (distance <= maximumDistance) {
                                builder.put(StringUtils.getLevenshteinDistance(word, input), word);
                            }
                        },
                        (lhsBuilder, rhsBuilder) -> lhsBuilder.putAll(rhsBuilder.build())
                ).build();
        // Fast-fail on inputs so bad that they have no suggestions
        if (distances.isEmpty()) {
            return Collections.emptyList();
        }

        final Integer minimumDistance = distances.keySet()
                .stream()
                .limit(maximumResponses)
                .min(Integer::compare)
                .orElseThrow(ThrowableUtils.fail(
                        "No minimum distance found in a distance mapping of %s for an input of %s!", distances, input));
        return distances.get(minimumDistance);
    }
}
