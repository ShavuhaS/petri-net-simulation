package ua.kpi.petri.observer;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.simulation.SimulationResult;

/**
 * Receives simulation events. Tick 0 is the initial marking, available in {@link #onStart}.
 */
public interface SimulationListener {

    default void onStart(PetriNet net) {
    }

    default void onFire(int tick, Transition transition) {
    }

    default void onTickEnd(int tick) {
    }

    default void onEnd(SimulationResult result) {
    }
}
