package ua.kpi.petri.simulation;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.observer.SimulationListener;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Independent runs of one net. Run i uses seed {@code baseSeed + i}, so any run can be repeated on its own.
 */
public final class SimulationSeries {

    private final PetriNet net;
    private final long baseSeed;
    private final int maxTicks;

    public SimulationSeries(PetriNet net, long baseSeed, int maxTicks) {
        if (maxTicks < 0) {
            throw new IllegalArgumentException("maxTicks must not be negative: " + maxTicks);
        }
        this.net = net;
        this.baseSeed = baseSeed;
        this.maxTicks = maxTicks;
    }

    public PetriNet net() {
        return net;
    }

    public long baseSeed() {
        return baseSeed;
    }

    public int maxTicks() {
        return maxTicks;
    }

    public long seedOf(int run) {
        return baseSeed + run;
    }

    public SimulationResult run(int run, SimulationListener... listeners) {
        Simulator simulator = new Simulator(net, new NaiveEnabledTransitionsFinder(net), newRandom(seedOf(run)));
        for (SimulationListener listener : listeners) {
            simulator.addListener(listener);
        }
        return simulator.run(maxTicks);
    }

    public static RandomGenerator newRandom(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }
}
