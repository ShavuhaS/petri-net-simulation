package ua.kpi.petri.simulation;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.observer.SimulationListener;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Independent runs of one net. Run i uses seed {@code baseSeed + i}, so any run can be repeated on its own.
 * Every run finds enabled transitions with {@code finder}: full scan by default.
 */
public final class SimulationSeries {

    private final PetriNet net;
    private final long baseSeed;
    private final int maxTicks;
    private final FinderType finder;

    public SimulationSeries(PetriNet net, long baseSeed, int maxTicks) {
        this(net, baseSeed, maxTicks, FinderType.NAIVE);
    }

    public SimulationSeries(PetriNet net, long baseSeed, int maxTicks, FinderType finder) {
        if (maxTicks < 0) {
            throw new IllegalArgumentException("maxTicks must not be negative: " + maxTicks);
        }
        this.net = net;
        this.baseSeed = baseSeed;
        this.maxTicks = maxTicks;
        this.finder = finder;
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

    public FinderType finder() {
        return finder;
    }

    public long seedOf(int run) {
        return baseSeed + run;
    }

    public SimulationResult run(int run, SimulationListener... listeners) {
        Simulator simulator = new Simulator(net, finder.create(net), newRandom(seedOf(run)));
        for (SimulationListener listener : listeners) {
            simulator.addListener(listener);
        }
        return simulator.run(maxTicks);
    }

    public static RandomGenerator newRandom(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }
}
