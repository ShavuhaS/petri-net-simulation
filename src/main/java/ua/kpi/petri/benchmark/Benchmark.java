package ua.kpi.petri.benchmark;

import ua.kpi.petri.analysis.RunningStatistics;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.simulation.EnabledTransitionsFinder;
import ua.kpi.petri.simulation.FinderType;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.SimulationSeries;
import ua.kpi.petri.simulation.Simulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Time of one simulation step. For each net and finder: {@code warmup} runs that are not recorded
 * (the JIT compiler settles), then {@code runs} measured runs of {@code ticks} ticks without listeners.
 * Only {@link Simulator#run} is timed; run i uses seed {@code seed + i}.
 */
public final class Benchmark {

    private final int ticks;
    private final int warmup;
    private final int runs;
    private final long seed;

    public Benchmark(int ticks, int warmup, int runs, long seed) {
        if (ticks < 1 || warmup < 0 || runs < 1) {
            throw new IllegalArgumentException("Need ticks >= 1, warmup >= 0 and runs >= 1");
        }
        this.ticks = ticks;
        this.warmup = warmup;
        this.runs = runs;
        this.seed = seed;
    }

    public record Measurement(int run, long seed, int ticks, long nanos) {

        public double nanosPerTick() {
            return (double) nanos / ticks;
        }
    }

    public record Point(PetriNet net, FinderType finder, List<Measurement> measurements) {

        private double[] sorted() {
            return measurements.stream().mapToDouble(Measurement::nanosPerTick).sorted().toArray();
        }

        public double median() {
            double[] values = sorted();
            int middle = values.length / 2;
            return values.length % 2 == 1 ? values[middle] : (values[middle - 1] + values[middle]) / 2;
        }

        public double min() {
            return sorted()[0];
        }

        public double max() {
            double[] values = sorted();
            return values[values.length - 1];
        }

        public double mean() {
            return statistics().mean();
        }

        /** Sample standard deviation; NaN for a single run. */
        public double standardDeviation() {
            return Math.sqrt(statistics().variance());
        }

        private RunningStatistics statistics() {
            RunningStatistics statistics = new RunningStatistics();
            Arrays.stream(sorted()).forEach(statistics::add);
            return statistics;
        }
    }

    public Point measure(PetriNet net, FinderType type) {
        EnabledTransitionsFinder finder = type.create(net);
        for (int i = 0; i < warmup; i++) {
            new Simulator(net, finder, SimulationSeries.newRandom(seed + i)).run(ticks);
        }
        List<Measurement> measurements = new ArrayList<>(runs);
        for (int i = 0; i < runs; i++) {
            Simulator simulator = new Simulator(net, finder, SimulationSeries.newRandom(seed + i));
            long start = System.nanoTime();
            SimulationResult result = simulator.run(ticks);
            long nanos = System.nanoTime() - start;
            measurements.add(new Measurement(i, seed + i, result.ticks(), nanos));
        }
        return new Point(net, type, List.copyOf(measurements));
    }
}
