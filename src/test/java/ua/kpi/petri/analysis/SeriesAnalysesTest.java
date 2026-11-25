package ua.kpi.petri.analysis;

import org.junit.jupiter.api.Test;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.simulation.SimulationSeries;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesAnalysesTest {

    private static final String GENERATOR = """
            { "name": "gen", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" } ],
              "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } } ] }
            """;

    private static final String WITH_DEAD_TRANSITION = """
            { "name": "dead", "places": [ { "name": "P", "tokens": 1 }, { "name": "Q" } ],
              "transitions": [ { "name": "Move", "in": { "P": 1 }, "out": { "Q": 1 } },
                               { "name": "Never", "in": { "P": 2 }, "out": { "Q": 1 } } ] }
            """;

    private static PetriNet example(String file) {
        return NetJson.read(Path.of("examples", file));
    }

    @Test
    void deterministicNetHasZeroVarianceAndAutoStopsAfterPilot() {
        MarkingStatisticsAnalysis.Result fixed =
                new MarkingStatisticsAnalysis(new SimulationSeries(example("shared-resource.json"), 1, 100), 0.95)
                        .runFixed(5);
        MarkingStatisticsAnalysis.PlaceSummary queue = fixed.places().getFirst();
        assertEquals(9.0 / 7, queue.mean(), 1e-12);
        assertEquals(0, queue.variance(), 1e-12);
        assertEquals(0, queue.error(), 1e-12);
        assertEquals(5, fixed.deadlockRuns());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), fixed.runs().stream().map(MarkingStatisticsAnalysis.RunSummary::seed).toList());

        MarkingStatisticsAnalysis.Result auto =
                new MarkingStatisticsAnalysis(new SimulationSeries(example("shared-resource.json"), 1, 100), 0.95)
                        .runAuto(20, 0.05, 10_000);
        assertEquals(20, auto.runCount());
        assertEquals(0, auto.requiredRuns());
    }

    @Test
    void autoRunsAsManyAsChebyshevRequiresUpToTheLimit() {
        MarkingStatisticsAnalysis.Result result =
                new MarkingStatisticsAnalysis(new SimulationSeries(example("lecture7-abc.json"), 7, 100), 0.95)
                        .runAuto(20, 0.05, 10_000);
        assertEquals(20, result.pilotRuns());
        assertTrue(result.requiredRuns() > 20, "required " + result.requiredRuns());
        assertEquals(Math.min(result.requiredRuns(), 10_000), result.runCount());

        MarkingStatisticsAnalysis.Result limited =
                new MarkingStatisticsAnalysis(new SimulationSeries(example("lecture7-abc.json"), 7, 100), 0.95)
                        .runAuto(20, 0.001, 50);
        assertEquals(50, limited.runCount());
    }

    @Test
    void analysisObjectRunsOneSeries() {
        MarkingStatisticsAnalysis analysis =
                new MarkingStatisticsAnalysis(new SimulationSeries(example("lecture7-abc.json"), 1, 100), 0.95);
        analysis.runFixed(2);
        assertThrows(IllegalStateException.class, () -> analysis.runFixed(2));
    }

    @Test
    void simulatedReachabilitySaturatesAtTheExactSet() {
        PetriNet net = example("lecture7-abc.json");
        SimulatedReachability.Result result =
                new SimulatedReachability(new SimulationSeries(net, 1, 100), 0.95).runUntilSaturated(30, 10_000);

        assertEquals(30, result.runsWithoutNew());
        assertEquals(Set.copyOf(new ExactReachabilityAnalyzer(net).analyze().markings()), Set.copyOf(result.markings()));
        assertEquals(Set.of(new Marking(0, 0, 0, 1), new Marking(0, 0, 2, 0)), Set.copyOf(result.terminal()));
        assertEquals(6, result.newPerRun().stream().mapToInt(Integer::intValue).sum());
        assertEquals(StatisticalBounds.zeroOccurrenceBound(30, 0.95), result.newMarkingBound(), 1e-12);
    }

    @Test
    void simulatedReachabilityCountsNewMarkingsPerRun() {
        SimulatedReachability.Result result =
                new SimulatedReachability(new SimulationSeries(NetJson.parse(GENERATOR), 1, 10), 0.95).runFixed(3);

        assertEquals(List.of(2, 0, 0), result.newPerRun());
        assertEquals(List.of(new Marking(1, 0), new Marking(1, Marking.OMEGA)), result.markings());
        assertEquals(2, result.runsWithoutNew());
        assertEquals(StatisticalBounds.zeroOccurrenceBound(2, 0.95), result.newMarkingBound(), 1e-12);
        assertEquals(0, result.deadlockRuns());
    }

    @Test
    void activityFindsDeadTransitionAndExcludesLevel4OnDeadlock() {
        ActivityAnalysis.Result result =
                ActivityAnalysis.run(new SimulationSeries(NetJson.parse(WITH_DEAD_TRANSITION), 1, 100), 50, 0.95);

        ActivityAnalysis.TransitionActivity move = result.transitions().get(0);
        ActivityAnalysis.TransitionActivity never = result.transitions().get(1);
        assertEquals(50, move.runsFired());
        assertEquals(1.0, move.meanFirings(), 1e-12);
        assertFalse(never.fired());
        assertEquals(StatisticalBounds.zeroOccurrenceBound(50, 0.95), result.deadBound(), 1e-12);
        assertTrue(result.level4Excluded());
    }

    @Test
    void activityShareOfLecture7TransitionC() {
        ActivityAnalysis.Result result =
                ActivityAnalysis.run(new SimulationSeries(example("lecture7-abc.json"), 1, 100), 2000, 0.95);
        ActivityAnalysis.TransitionActivity c = result.transitions().get(2);

        assertEquals("C", c.transition());
        assertEquals(0.05, c.shareError(), 1e-12);
        assertTrue(Math.abs(c.share() - 0.25) < c.shareError(), "share " + c.share());
        assertEquals(2000, ActivityAnalysis.requiredRuns(0.05, 0.95));
    }

    @Test
    void generatorNeverDeadlocks() {
        ActivityAnalysis.Result result =
                ActivityAnalysis.run(new SimulationSeries(NetJson.parse(GENERATOR), 1, 20), 10, 0.95);
        assertFalse(result.level4Excluded());
        assertEquals(20.0, result.transitions().getFirst().meanFirings(), 1e-12);
    }
}
