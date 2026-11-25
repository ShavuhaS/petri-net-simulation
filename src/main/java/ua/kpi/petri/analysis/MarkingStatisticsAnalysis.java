package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Place;
import ua.kpi.petri.observer.MarkingStatistics;
import ua.kpi.petri.observer.MarkingStatistics.PlaceStatistics;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.SimulationSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Marking statistics over a series of runs. The response of one run is the mean marking of each place;
 * the series gives the mean of these responses, their variance, and the overall minimum and maximum.
 * One object runs one series.
 */
public final class MarkingStatisticsAnalysis {

    /**
     * @param mean     mean over runs of the per-run mean marking
     * @param variance sample variance of the per-run mean marking (NaN for one run)
     * @param error    epsilon that Chebyshev's inequality guarantees for {@code mean} at the series' confidence
     */
    public record PlaceSummary(String place, double mean, double variance, double error, int min, int max) {
    }

    public record RunSummary(int run, long seed, SimulationResult result, double[] means) {
    }

    /**
     * @param requiredRuns runs required by Chebyshev's inequality after the pilot series; -1 for a fixed number of runs
     */
    public record Result(List<PlaceSummary> places, List<RunSummary> runs, int deadlockRuns, double confidence,
                         int pilotRuns, int requiredRuns) {

        public int runCount() {
            return runs.size();
        }
    }

    private final SimulationSeries series;
    private final double confidence;
    private final List<Place> places;
    private final RunningStatistics[] responses;
    private final int[] mins;
    private final int[] maxs;
    private final List<RunSummary> runs = new ArrayList<>();
    private int deadlockRuns;

    public MarkingStatisticsAnalysis(SimulationSeries series, double confidence) {
        this.series = series;
        this.confidence = confidence;
        this.places = series.net().getPlaces();
        int n = places.size();
        responses = new RunningStatistics[n];
        for (int p = 0; p < n; p++) {
            responses[p] = new RunningStatistics();
        }
        mins = new int[n];
        maxs = new int[n];
        Arrays.fill(mins, Integer.MAX_VALUE);
    }

    public Result runFixed(int runCount) {
        requireFresh();
        if (runCount < 1) {
            throw new IllegalArgumentException("Number of runs must be positive: " + runCount);
        }
        runUntil(runCount);
        return result(0, -1);
    }

    /**
     * Runs a pilot series, estimates the variance of each place's response and continues until the number
     * of runs required by Chebyshev's inequality for the worst place, but not beyond {@code maxRuns}.
     */
    public Result runAuto(int pilotRuns, double epsilon, int maxRuns) {
        requireFresh();
        if (pilotRuns < 2) {
            throw new IllegalArgumentException("Pilot series needs at least 2 runs to estimate variance");
        }
        runUntil(pilotRuns);
        int required = 0;
        for (int p = 0; p < places.size(); p++) {
            required = Math.max(required,
                    StatisticalBounds.chebyshevRuns(variance(p), epsilon, confidence));
        }
        runUntil(Math.min(Math.max(required, pilotRuns), maxRuns));
        return result(pilotRuns, required);
    }

    private void requireFresh() {
        if (!runs.isEmpty()) {
            throw new IllegalStateException("An analysis object runs one series only");
        }
    }

    private void runUntil(int runCount) {
        while (runs.size() < runCount) {
            int run = runs.size();
            MarkingStatistics statistics = new MarkingStatistics();
            SimulationResult result = series.run(run, statistics);
            if (result.deadlocked()) {
                deadlockRuns++;
            }
            List<PlaceStatistics> stats = statistics.getStatistics();
            double[] means = new double[stats.size()];
            for (int p = 0; p < stats.size(); p++) {
                PlaceStatistics s = stats.get(p);
                means[p] = s.mean();
                responses[p].add(s.mean());
                mins[p] = Math.min(mins[p], s.min());
                maxs[p] = Math.max(maxs[p], s.max());
            }
            runs.add(new RunSummary(run, series.seedOf(run), result, means));
        }
    }

    private double variance(int place) {
        return responses[place].variance();
    }

    private Result result(int pilotRuns, int requiredRuns) {
        List<PlaceSummary> summaries = new ArrayList<>(places.size());
        for (int p = 0; p < places.size(); p++) {
            double variance = variance(p);
            double error = Double.isNaN(variance) ? Double.NaN
                    : StatisticalBounds.chebyshevError(variance, runs.size(), confidence);
            summaries.add(new PlaceSummary(places.get(p).getName(), responses[p].mean(), variance, error,
                    mins[p], maxs[p]));
        }
        return new Result(summaries, List.copyOf(runs), deadlockRuns, confidence, pilotRuns, requiredRuns);
    }
}
