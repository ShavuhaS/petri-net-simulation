package ua.kpi.petri.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Transition {

    private static final int UNASSIGNED = -1;

    private final String name;
    private final List<Arc> inputs;
    private final List<Arc> outputs;
    private int index = UNASSIGNED;

    public Transition(String name, List<Arc> inputs, List<Arc> outputs) {
        this.name = Place.requireName(name);
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        if (this.inputs.isEmpty() || this.outputs.isEmpty()) {
            throw new IllegalArgumentException("Transition '" + name
                    + "' must have at least one input place and at least one output place");
        }
        requireDistinctPlaces(this.inputs, "input");
        requireDistinctPlaces(this.outputs, "output");
    }

    public String getName() {
        return name;
    }

    /** Position of this transition in {@link PetriNet#getTransitions()}; lets per-transition data live in arrays. */
    public int getIndex() {
        if (index == UNASSIGNED) {
            throw new IllegalStateException("Transition '" + name + "' does not belong to a net");
        }
        return index;
    }

    boolean isIndexAssigned() {
        return index != UNASSIGNED;
    }

    void assignIndex(int index) {
        this.index = index;
    }

    public List<Arc> getInputs() {
        return inputs;
    }

    public List<Arc> getOutputs() {
        return outputs;
    }

    public boolean isEnabled() {
        for (Arc arc : inputs) {
            if (arc.place().getTokens() < arc.weight()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fires atomically: consumes input tokens and produces output tokens in one action,
     * as in the classical Petri net (no time delay).
     */
    public void fire() {
        if (!isEnabled()) {
            throw new IllegalStateException("Transition '" + name + "' is not enabled");
        }
        for (Arc arc : inputs) {
            arc.place().removeTokens(arc.weight());
        }
        for (Arc arc : outputs) {
            arc.place().addTokens(arc.weight());
        }
    }

    /** Firing condition checked against a given marking instead of the current state of the net. */
    public boolean isEnabledIn(Marking marking) {
        for (Arc arc : inputs) {
            if (marking.get(arc.place().getIndex()) < arc.weight()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Marking that results from firing in the given marking; the net itself is not changed.
     * A place with {@link Marking#OMEGA} stays unbounded whatever is added or removed.
     */
    public Marking fire(Marking marking) {
        if (!isEnabledIn(marking)) {
            throw new IllegalStateException("Transition '" + name + "' is not enabled in " + marking);
        }
        int[] tokens = marking.toArray();
        for (Arc arc : inputs) {
            int p = arc.place().getIndex();
            if (tokens[p] != Marking.OMEGA) {
                tokens[p] -= arc.weight();
            }
        }
        for (Arc arc : outputs) {
            int p = arc.place().getIndex();
            if (tokens[p] != Marking.OMEGA) {
                tokens[p] += arc.weight();
            }
        }
        return new Marking(tokens);
    }

    private void requireDistinctPlaces(List<Arc> arcs, String kind) {
        Set<Place> seen = new HashSet<>();
        for (Arc arc : arcs) {
            if (!seen.add(arc.place())) {
                throw new IllegalArgumentException("Transition '" + name + "' has duplicate " + kind
                        + " arc for place '" + arc.place().getName() + "'");
            }
        }
    }

    @Override
    public String toString() {
        return name + ": " + inputs + " -> " + outputs;
    }
}
