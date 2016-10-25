package dex.util;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class SpellingSuggesterTest
{
    private static final String DICTIONARY_URL = "pokemon.txt";

    private final ImmutableSet<String> dictionary_;

    public SpellingSuggesterTest() throws IOException
    {
        final ClassLoader classLoader = getClass().getClassLoader();
        try (final InputStream dictionaryStream = classLoader.getResourceAsStream(DICTIONARY_URL);
             final BufferedReader dictionaryReader = new BufferedReader(new InputStreamReader(dictionaryStream))) {
            dictionary_ = ImmutableSet.copyOf(dictionaryReader.lines()
                    .map(ParsingUtils::comparisonFormat)
                    .collect(Collectors.toSet()));
        }
    }

    @Test
    public void suggest_exactMatches_matchesProvided()
    {
        final String input = "sneasel";
        final SpellingSuggester suggester = new SpellingSuggester(dictionary_);

        final Collection<String> suggested = suggester.suggest(input);
        assertEquals(Collections.singletonList(input), suggested);
    }

    @Test
    public void suggest_letterSwapped_matchesProvided()
    {
        final String input = "nseasel";
        final String expected = "sneasel";
        final SpellingSuggester suggester = new SpellingSuggester(dictionary_);

        final Collection<String> suggested = suggester.suggest(input);
        assertEquals(Collections.singletonList(expected), suggested);
    }

    @Test
    public void suggest_letterMissing_matchesProvided()
    {
        final String input = "seasel";
        final String expected = "sneasel";
        final SpellingSuggester suggester = new SpellingSuggester(dictionary_);

        final Collection<String> suggested = suggester.suggest(input);
        assertEquals(Collections.singletonList(expected), suggested);
    }

    @Test
    public void suggest_letterAdded_matchesProvided()
    {
        final String input = "snneasel";
        final String expected = "sneasel";
        final SpellingSuggester suggester = new SpellingSuggester(dictionary_);

        final Collection<String> suggested = suggester.suggest(input);
        assertEquals(Collections.singletonList(expected), suggested);
    }
}
