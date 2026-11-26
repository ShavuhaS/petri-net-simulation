package ua.kpi.petri.simulation;

import ua.kpi.petri.model.PetriNet;

/** The ways of finding enabled transitions that a run can use. */
public enum FinderType {
    NAIVE,
    INCREMENTAL;

    public EnabledTransitionsFinder create(PetriNet net) {
        return switch (this) {
            case NAIVE -> new NaiveEnabledTransitionsFinder(net);
            case INCREMENTAL -> new IncrementalEnabledTransitionsFinder(net);
        };
    }
}
