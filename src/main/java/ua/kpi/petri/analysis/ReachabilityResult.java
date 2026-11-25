package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of building the reachability tree.
 *
 * @param markings         distinct markings of the tree in breadth-first order, starting with the initial one;
 *                         markings with OMEGA stand for infinitely many reachable markings
 * @param edges            distinct firings between markings: the reachability graph
 * @param terminal         markings in which no transition is enabled (deadlocks)
 * @param deadTransitions  transitions that are not enabled in any marking of the tree (liveness level 0)
 * @param nodes            number of tree nodes, duplicates included
 * @param complete         false if the node limit was reached; then the sets above are partial
 */
public record ReachabilityResult(PetriNet net, List<Marking> markings, List<Edge> edges, List<Marking> terminal,
                                 List<String> deadTransitions, int nodes, boolean complete) {

    public record Edge(Marking from, String transition, Marking to) {
    }

    public boolean isBounded() {
        return markings.stream().noneMatch(Marking::hasOmega);
    }

    /** Largest token count of the place over all markings, or {@link Marking#OMEGA} if it is unbounded. */
    public int bound(Place place) {
        int max = 0;
        for (Marking marking : markings) {
            max = Math.max(max, marking.get(place.getIndex()));
        }
        return max;
    }

    /** The k of k-boundedness, or {@link Marking#OMEGA} if the net is unbounded. */
    public int boundK() {
        int k = 0;
        for (Place place : net.getPlaces()) {
            k = Math.max(k, bound(place));
        }
        return k;
    }

    public List<String> unboundedPlaces() {
        List<String> names = new ArrayList<>();
        for (Place place : net.getPlaces()) {
            if (bound(place) == Marking.OMEGA) {
                names.add(place.getName());
            }
        }
        return names;
    }
}
