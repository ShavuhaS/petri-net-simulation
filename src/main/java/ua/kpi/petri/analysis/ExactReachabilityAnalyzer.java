package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Builds the reachability tree of a net breadth-first.
 * The tree starts at the initial marking; a node is a leaf if no transition is enabled (terminal marking)
 * or its marking already occurred in the tree (duplicate marking). A place gets ω when a marking on the path
 * from the root is less than or equal to the new marking in every place and strictly less in that place.
 * Without ω the set of tree markings is exactly the set of reachable markings.
 */
public final class ExactReachabilityAnalyzer {

    public static final int DEFAULT_MAX_NODES = 100_000;

    private final PetriNet net;
    private final int maxNodes;

    private record Node(Marking marking, Node parent) {
    }

    public ExactReachabilityAnalyzer(PetriNet net) {
        this(net, DEFAULT_MAX_NODES);
    }

    public ExactReachabilityAnalyzer(PetriNet net, int maxNodes) {
        if (maxNodes < 1) {
            throw new IllegalArgumentException("maxNodes must be positive: " + maxNodes);
        }
        this.net = net;
        this.maxNodes = maxNodes;
    }

    public ReachabilityResult analyze() {
        List<Transition> transitions = net.getTransitions();
        Set<Marking> markings = new LinkedHashSet<>();
        Set<ReachabilityResult.Edge> edges = new LinkedHashSet<>();
        Set<Marking> terminal = new LinkedHashSet<>();
        boolean[] enabledSomewhere = new boolean[transitions.size()];

        Node root = new Node(net.initialMarking(), null);
        markings.add(root.marking());
        Queue<Node> queue = new ArrayDeque<>();
        queue.add(root);
        int nodes = 1;
        boolean complete = true;

        while (!queue.isEmpty()) {
            Node node = queue.poll();
            boolean anyEnabled = false;

            for (Transition transition : transitions) {
                if (!transition.isEnabledIn(node.marking())) {
                    continue;
                }
                anyEnabled = true;
                enabledSomewhere[transition.getIndex()] = true;
                if (nodes >= maxNodes) {
                    complete = false;
                    continue;
                }
                Marking next = OmegaRule.apply(transition.fire(node.marking()), pathTo(node));
                nodes++;
                edges.add(new ReachabilityResult.Edge(node.marking(), transition.getName(), next));
                if (markings.add(next)) {
                    queue.add(new Node(next, node));
                }
            }
            if (!anyEnabled) {
                terminal.add(node.marking());
            }
        }

        List<String> deadTransitions = new ArrayList<>();
        for (Transition transition : transitions) {
            if (!enabledSomewhere[transition.getIndex()]) {
                deadTransitions.add(transition.getName());
            }
        }
        return new ReachabilityResult(net, List.copyOf(markings), List.copyOf(edges),
                List.copyOf(terminal), deadTransitions, nodes, complete);
    }

    /** Markings from the root to {@code node}, the path along which ω is introduced. */
    private static List<Marking> pathTo(Node node) {
        List<Marking> path = new ArrayList<>();
        for (Node n = node; n != null; n = n.parent()) {
            path.add(n.marking());
        }
        return path;
    }
}
