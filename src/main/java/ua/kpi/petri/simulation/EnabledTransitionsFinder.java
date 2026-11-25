package ua.kpi.petri.simulation;

import ua.kpi.petri.model.Transition;

import java.util.List;

/**
 * Strategy for finding transitions whose firing condition holds in the current marking.
 */
public interface EnabledTransitionsFinder {

    /** The returned list must not be modified by the caller. */
    List<Transition> findEnabled();

    default void onFired(Transition transition) {
    }

    default void reset() {
    }
}
