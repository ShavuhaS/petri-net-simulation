package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.observer.SimulationListener;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.SimulationSeries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Set of reachable markings estimated by simulation: the union of the markings met in a series of runs.
 * Each run is a path from the initial marking, and ω is introduced along it as in the reachability tree,
 * so an unbounded place does not make the set grow without limit. The net itself is simulated with real
 * token counts; only the recorded markings carry ω. Runs that add no new marking indicate that the set
 * has saturated. One object runs one series.
 */
public final class SimulatedReachability {

    /**
     * @param markings          distinct markings, possibly with ω, in the order they were first met
     * @param newPerRun         number of markings first met in each run (the saturation curve)
     * @param terminal          dead markings in which runs ended
     * @param runsWithoutNew    number of final runs in a row that found no new marking
     */
    public record Result(List<Marking> markings, List<Integer> newPerRun, int deadlockRuns,
                         List<Marking> terminal, int runsWithoutNew, double confidence) {

        public int runCount() {
            return newPerRun.size();
        }

        /**
         * Upper bound, at the given confidence, of the probability that one more run finds a new marking;
         * NaN if the last run found something new.
         */
        public double newMarkingBound() {
            return runsWithoutNew == 0 ? Double.NaN
                    : StatisticalBounds.zeroOccurrenceBound(runsWithoutNew, confidence);
        }
    }

    private final SimulationSeries series;
    private final double confidence;
    private final Set<Marking> markings = new LinkedHashSet<>();
    private final Set<Marking> terminal = new LinkedHashSet<>();
    private final List<Integer> newPerRun = new ArrayList<>();
    private int deadlockRuns;
    private int runsWithoutNew;
    private int foundInRun;
    private final Set<Marking> runPath = new LinkedHashSet<>();
    private Marking lastRecorded;

    public SimulatedReachability(SimulationSeries series, double confidence) {
        this.series = series;
        this.confidence = confidence;
    }

    public Result runFixed(int runCount) {
        requireFresh();
        if (runCount < 1) {
            throw new IllegalArgumentException("Number of runs must be positive: " + runCount);
        }
        while (newPerRun.size() < runCount) {
            runOnce();
        }
        return result();
    }

    /** Runs until {@code window} runs in a row find no new marking, or until {@code maxRuns} runs. */
    public Result runUntilSaturated(int window, int maxRuns) {
        requireFresh();
        if (window < 1) {
            throw new IllegalArgumentException("Saturation window must be positive: " + window);
        }
        while (runsWithoutNew < window && newPerRun.size() < maxRuns) {
            runOnce();
        }
        return result();
    }

    private void requireFresh() {
        if (!newPerRun.isEmpty()) {
            throw new IllegalStateException("An analysis object runs one series only");
        }
    }

    private void runOnce() {
        int run = newPerRun.size();
        foundInRun = 0;
        runPath.clear();
        lastRecorded = null;
        PetriNet net = series.net();
        SimulationListener collector = new SimulationListener() {
            @Override
            public void onStart(PetriNet n) {
                record(net.snapshot());
            }

            @Override
            public void onTickEnd(int tick) {
                record(net.snapshot());
            }

            private void record(Marking concrete) {
                Marking marking = withOmegas(concrete);
                if (markings.add(marking)) {
                    foundInRun++;
                }
            }
        };
        SimulationResult result = series.run(run, collector);
        if (result.deadlocked()) {
            deadlockRuns++;
            terminal.add(lastRecorded);
        }
        newPerRun.add(foundInRun);
        runsWithoutNew = foundInRun == 0 ? runsWithoutNew + 1 : 0;
    }

    /**
     * Keeps the ω places of the previous marking of this run and applies the ω rule
     * against the earlier markings of the run.
     */
    private Marking withOmegas(Marking concrete) {
        int[] tokens = concrete.toArray();
        if (lastRecorded != null) {
            for (int p = 0; p < tokens.length; p++) {
                if (lastRecorded.isOmega(p)) {
                    tokens[p] = Marking.OMEGA;
                }
            }
        }
        Marking marking = OmegaRule.apply(new Marking(tokens), runPath);
        runPath.add(marking);
        lastRecorded = marking;
        return marking;
    }

    private Result result() {
        return new Result(List.copyOf(markings), List.copyOf(newPerRun), deadlockRuns,
                List.copyOf(terminal), runsWithoutNew, confidence);
    }
}
