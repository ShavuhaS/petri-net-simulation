package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Marking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Comparison of the markings met in simulation with the reachability tree.
 * Every reachable marking is covered by some marking of the tree, so a marking met in simulation
 * that no tree marking covers would mean an error in the simulator or in the tree.
 *
 * @param inTree            markings met in simulation that are also markings of the tree
 * @param treeMarkings      number of markings of the tree
 * @param notCovered        markings met in simulation that no tree marking covers (must be empty)
 * @param deadMet           dead markings of the tree that some run ended in
 * @param deadInTree        number of dead markings of the tree
 */
public record ReachabilityComparison(int inTree, int treeMarkings, List<Marking> notCovered,
                                     int deadMet, int deadInTree) {

    public static ReachabilityComparison of(SimulatedReachability.Result simulated, ReachabilityResult tree) {
        if (!tree.complete()) {
            throw new IllegalArgumentException("The reachability tree is incomplete: node limit reached");
        }
        Set<Marking> treeSet = new HashSet<>(tree.markings());
        int inTree = 0;
        List<Marking> notCovered = new ArrayList<>();
        for (Marking marking : simulated.markings()) {
            if (treeSet.contains(marking)) {
                inTree++;
            } else if (tree.markings().stream().noneMatch(marking::isCoveredBy)) {
                notCovered.add(marking);
            }
        }
        Set<Marking> terminalMet = new HashSet<>(simulated.terminal());
        int deadMet = (int) tree.terminal().stream().filter(terminalMet::contains).count();
        return new ReachabilityComparison(inTree, treeSet.size(), List.copyOf(notCovered), deadMet,
                tree.terminal().size());
    }
}
