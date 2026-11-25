package ua.kpi.petri.simulation;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.observer.SimulationListener;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Simulation of a classical Petri net (Lecture 10, slide 8): in each tick one of the enabled
 * transitions is chosen with equal probability and fired. The run stops on deadlock
 * (no enabled transitions) or after {@code maxTicks}.
 */
public final class Simulator {

    private final PetriNet net;
    private final EnabledTransitionsFinder finder;
    private final RandomGenerator random;
    private final List<SimulationListener> listeners = new ArrayList<>();

    public Simulator(PetriNet net, EnabledTransitionsFinder finder, RandomGenerator random) {
        this.net = net;
        this.finder = finder;
        this.random = random;
    }

    public Simulator addListener(SimulationListener listener) {
        listeners.add(listener);
        return this;
    }

    public SimulationResult run(int maxTicks) {
        if (maxTicks < 0) {
            throw new IllegalArgumentException("maxTicks must not be negative: " + maxTicks);
        }
        net.reset();
        finder.reset();
        for (SimulationListener listener : listeners) {
            listener.onStart(net);
        }

        int tick = 0;
        StopReason stopReason = StopReason.MAX_TICKS;
        while (tick < maxTicks) {
            List<Transition> enabled = finder.findEnabled();
            if (enabled.isEmpty()) {
                stopReason = StopReason.DEADLOCK;
                break;
            }
            tick++;
            Transition chosen = enabled.get(random.nextInt(enabled.size()));
            chosen.fire();
            finder.onFired(chosen);
            for (SimulationListener listener : listeners) {
                listener.onFire(tick, chosen);
            }
            for (SimulationListener listener : listeners) {
                listener.onTickEnd(tick);
            }
        }

        SimulationResult result = new SimulationResult(tick, stopReason, net.snapshot());
        for (SimulationListener listener : listeners) {
            listener.onEnd(result);
        }
        return result;
    }
}
