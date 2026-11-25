package ua.kpi.petri.analysis;

/**
 * Mean and sample variance of a stream of values, updated one value at a time
 * (Welford's method).
 */
public final class RunningStatistics {

    private long count;
    private double mean;
    private double sumOfSquaredDeviations;

    public void add(double value) {
        count++;
        double delta = value - mean;
        mean += delta / count;
        sumOfSquaredDeviations += delta * (value - mean);
    }

    public long count() {
        return count;
    }

    public double mean() {
        return count == 0 ? Double.NaN : mean;
    }

    /** Unbiased sample variance */
    public double variance() {
        return count < 2 ? Double.NaN : sumOfSquaredDeviations / (count - 1);
    }
}
