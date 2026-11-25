package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Transition;
import ua.kpi.petri.observer.FiringCounter;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.SimulationSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * Liveness of transitions judged from a series of runs:
 * a transition that fired at least once has liveness level at least 1;
 * a transition that never fired has level 0 with the confidence of {@link Result#deadBound()};
 * if any run ended in a deadlock, no transition has level 4.
 */
public final class ActivityAnalysis {

    /**
     * @param runsFired   number of runs in which the transition fired at least once
     * @param share       runsFired / runs: estimate of the probability to fire in a run
     * @param shareError  epsilon that Chebyshev's inequality guarantees for {@code share}
     * @param meanFirings mean number of firings per run
     */
    public record TransitionActivity(String transition, int runsFired, double share, double shareError,
                                     double meanFirings) {

        public boolean fired() {
            return runsFired > 0;
        }
    }

    public record Result(List<TransitionActivity> transitions, int runs, int deadlockRuns, double confidence) {

        /** If a transition never fired, its probability to fire in a run is below this value. */
        public double deadBound() {
            return StatisticalBounds.zeroOccurrenceBound(runs, confidence);
        }

        /** A dead marking is reachable, and from it no transition can ever fire again. */
        public boolean level4Excluded() {
            return deadlockRuns > 0;
        }
    }

    private ActivityAnalysis() {
    }

    /** Runs needed to estimate every share within epsilon, using the largest variance of a 0/1 outcome. */
    public static int requiredRuns(double epsilon, double confidence) {
        return StatisticalBounds.chebyshevRuns(StatisticalBounds.PROPORTION_MAX_VARIANCE, epsilon, confidence);
    }

    public static Result run(SimulationSeries series, int runCount, double confidence) {
        if (runCount < 1) {
            throw new IllegalArgumentException("Number of runs must be positive: " + runCount);
        }
        List<Transition> transitions = series.net().getTransitions();
        int[] runsFired = new int[transitions.size()];
        long[] firings = new long[transitions.size()];
        int deadlockRuns = 0;

        for (int run = 0; run < runCount; run++) {
            FiringCounter counter = new FiringCounter();
            SimulationResult result = series.run(run, counter);
            if (result.deadlocked()) {
                deadlockRuns++;
            }
            for (Transition t : transitions) {
                long count = counter.count(t);
                firings[t.getIndex()] += count;
                if (count > 0) {
                    runsFired[t.getIndex()]++;
                }
            }
        }

        double shareError = StatisticalBounds.chebyshevError(
                StatisticalBounds.PROPORTION_MAX_VARIANCE, runCount, confidence);
        List<TransitionActivity> activity = new ArrayList<>(transitions.size());
        for (Transition t : transitions) {
            int i = t.getIndex();
            activity.add(new TransitionActivity(t.getName(), runsFired[i], (double) runsFired[i] / runCount,
                    shareError, (double) firings[i] / runCount));
        }
        return new Result(activity, runCount, deadlockRuns, confidence);
    }
}
