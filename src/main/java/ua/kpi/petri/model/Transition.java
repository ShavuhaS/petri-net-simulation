package ua.kpi.petri.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Transition {

    private final String name;
    private final List<Arc> inputs;
    private final List<Arc> outputs;

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
