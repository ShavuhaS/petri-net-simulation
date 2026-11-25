package ua.kpi.petri.simulation;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the firing condition of every transition on each call: O(number of arcs).
 */
public final class NaiveEnabledTransitionsFinder implements EnabledTransitionsFinder {

    private final List<Transition> transitions;
    private final List<Transition> enabled;

    public NaiveEnabledTransitionsFinder(PetriNet net) {
        this.transitions = net.getTransitions();
        this.enabled = new ArrayList<>(transitions.size());
    }

    @Override
    public List<Transition> findEnabled() {
        enabled.clear();
        for (Transition transition : transitions) {
            if (transition.isEnabled()) {
                enabled.add(transition);
            }
        }
        return enabled;
    }
}
