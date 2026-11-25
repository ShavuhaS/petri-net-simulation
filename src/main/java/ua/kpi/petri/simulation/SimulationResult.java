package ua.kpi.petri.simulation;

import ua.kpi.petri.model.Marking;

/**
 * @param ticks        number of executed ticks, equal to the number of firings; on deadlock,
 *                     the tick at which the dead marking was reached
 * @param stopReason   why the run ended
 * @param finalMarking marking after the last tick
 */
public record SimulationResult(int ticks, StopReason stopReason, Marking finalMarking) {

    public boolean deadlocked() {
        return stopReason == StopReason.DEADLOCK;
    }
}
