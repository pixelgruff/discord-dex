package dex.util;

import me.sargunvohra.lib.pokekotlin.model.ChainLink;
import me.sargunvohra.lib.pokekotlin.model.EvolutionChain;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class EvolutionUtils
{
    public static Optional<ChainLink> getPriorEvolution(final EvolutionChain chain, final String name)
    {
        final ChainLink priorLink = getPriorEvolution(chain.getChain(), name);
        return Optional.ofNullable(priorLink);
    }

    public static List<ChainLink> getFutureEvolution(final EvolutionChain chain, final String name)
    {
        final Optional<ChainLink> maybeCurrentEvolution = getMatchingEvolution(chain.getChain(),
                link -> link.getSpecies().getName().equals(name));
        Validate.isTrue(maybeCurrentEvolution.isPresent(), String.format(
                "Could not find %s in the evolution chain starting with %s!", name, chain.getChain()));
        return maybeCurrentEvolution.get().getEvolvesTo();
    }

    /**
     * Returns an ordered list of Pokemon evolutions, from earliest to latest
     * @param evolutionChain
     * @return
     */
    public static List<ChainLink> unravelEvolutionChain(final EvolutionChain evolutionChain)
    {
        return unravelChainLink(new ArrayList<>(3), evolutionChain.getChain());
    }

    private static ChainLink getPriorEvolution(final ChainLink baseLink, final String name)
    {
        for (final ChainLink evolvedLink : baseLink.getEvolvesTo()) {
            // Check if an evolution of this stage has the name we're matching against
            if (evolvedLink.getSpecies().getName().equals(name)) {
                return baseLink;
            }
            // Continue the search on the next layer
            final ChainLink resultLink = getPriorEvolution(evolvedLink, name);
            if (resultLink != null) {
                return resultLink;
            }
        }
        // If we found nothing, return nothing
        return null;
    }

    /**
     * Search this evolution chain, returning the first evolution that passes the given test
     */
    private static Optional<ChainLink> getMatchingEvolution(final ChainLink priorLink, final Predicate<ChainLink> tester)
    {
        // Classic breadth-first-search
        if (tester.test(priorLink)) {
            return Optional.of(priorLink);
        } else {
            for (final ChainLink futureLink : priorLink.getEvolvesTo()) {
                final Optional<ChainLink> resultLink = getMatchingEvolution(futureLink, tester);
                if (resultLink != null) {
                    return resultLink;
                }
            }
        }
        return Optional.empty();
    }

    private static List<ChainLink> unravelChainLink(final List<ChainLink> list, final ChainLink priorLink)
    {
        if (priorLink == null) {
            return Collections.emptyList();
        }

        list.add(priorLink);
        for (final ChainLink latterLink : priorLink.getEvolvesTo())
        {
            list.addAll(unravelChainLink(list, latterLink));
        }
        return list;
    }
}
