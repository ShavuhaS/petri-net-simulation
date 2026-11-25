package ua.kpi.petri.simulation;

public enum StopReason {
    /** The tick limit was reached. */
    MAX_TICKS,
    /** No transition is enabled in the current marking. */
    DEADLOCK
}
