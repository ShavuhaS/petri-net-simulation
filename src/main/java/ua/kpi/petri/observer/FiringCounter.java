package ua.kpi.petri.observer;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FiringCounter implements SimulationListener {

    private PetriNet net;
    private long[] counts;

    @Override
    public void onStart(PetriNet net) {
        this.net = net;
        counts = new long[net.getTransitions().size()];
    }

    @Override
    public void onFire(int tick, Transition transition) {
        counts[transition.getIndex()]++;
    }

    public long count(Transition transition) {
        return counts[transition.getIndex()];
    }

    /** Firing counts by transition name, in the order of the net's transitions. */
    public Map<String, Long> getCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Transition transition : net.getTransitions()) {
            result.put(transition.getName(), count(transition));
        }
        return Collections.unmodifiableMap(result);
    }
}
