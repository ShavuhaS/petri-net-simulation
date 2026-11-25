package ua.kpi.petri.analysis;

/**
 * Bounds used to plan and interpret a series of independent simulation runs.
 * None of them assumes a particular distribution of the response.
 */
public final class StatisticalBounds {

    /**
     * A 0/1 outcome with probability q has variance q(1 - q), which is at most 1/4.
     */
    public static final double PROPORTION_MAX_VARIANCE = 0.25;

    private StatisticalBounds() {
    }

    /**
     * Number of runs n such that the mean of n runs differs from the true mean by
     * less than {@code epsilon}
     * with probability at least {@code confidence}. Derived from Chebyshev's
     * inequality
     */
    public static int chebyshevRuns(double variance, double epsilon, double confidence) {
        requireConfidence(confidence);
        if (epsilon <= 0) {
            throw new IllegalArgumentException("epsilon must be positive: " + epsilon);
        }
        return (int) Math.ceil(variance / ((1 - confidence) * epsilon * epsilon));
    }

    /**
     * The epsilon that Chebyshev's inequality guarantees for a mean of {@code runs}
     * runs; see {@link #chebyshevRuns}.
     */
    public static double chebyshevError(double variance, int runs, double confidence) {
        requireConfidence(confidence);
        return Math.sqrt(variance / ((1 - confidence) * runs));
    }

    /**
     * If an event did not happen in any of {@code runs} independent runs, then with
     * the given confidence its
     * probability per run is below the returned q. The chance of seeing no event in
     * n runs is (1 - q)^n;
     * q is the value at which this chance equals 1 - confidence, so any larger q is
     * rejected.
     */
    public static double zeroOccurrenceBound(int runs, double confidence) {
        requireConfidence(confidence);
        if (runs < 1) {
            throw new IllegalArgumentException("runs must be positive: " + runs);
        }
        return 1 - Math.pow(1 - confidence, 1.0 / runs);
    }

    private static void requireConfidence(double confidence) {
        if (!(confidence > 0 && confidence < 1)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1: " + confidence);
        }
    }
}
