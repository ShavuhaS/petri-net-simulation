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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.petri.model.Marking.OMEGA;

class OmegaReachabilityTest {

    /** Single-server queue with an arrival generator: Queue and Served grow without limit. */
    private static final String QUEUE = """
            { "name": "queue",
              "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" }, { "name": "Free", "tokens": 1 },
                          { "name": "Busy" }, { "name": "Served" } ],
              "transitions": [
                { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } },
                { "name": "Start", "in": { "Queue": 1, "Free": 1 }, "out": { "Busy": 1 } },
                { "name": "Finish", "in": { "Busy": 1 }, "out": { "Free": 1, "Served": 1 } } ] }
            """;

    private static final String GENERATOR = """
            { "name": "gen", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" } ],
              "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } } ] }
            """;

    @Test
    void omegaRuleSetsOmegaWhereAnEarlierCoveredMarkingIsSmaller() {
        List<Marking> path = List.of(new Marking(1, 0, 3), new Marking(0, 1, 4));

        assertEquals(new Marking(1, OMEGA, 3), OmegaRule.apply(new Marking(1, 1, 3), path));
        Marking notCovering = new Marking(0, 0, 5);
        assertSame(notCovering, OmegaRule.apply(notCovering, path));
        assertEquals(new Marking(1, 0, 3), OmegaRule.apply(new Marking(1, 0, 3), path));
        assertEquals(new Marking(OMEGA, OMEGA, 3),
                OmegaRule.apply(new Marking(1, 1, 3), List.of(new Marking(1, 0, 3), new Marking(0, 1, 3))),
                "every covered earlier marking adds its ω places");
    }

    @Test
    void simulationOfGeneratorGivesTheSameOmegaMarkingsAsTheTree() {
        PetriNet net = NetJson.parse(GENERATOR);
        SimulatedReachability.Result simulated =
                new SimulatedReachability(new SimulationSeries(net, 1, 1000), 0.95).runUntilSaturated(10, 1000);

        assertEquals(List.of(new Marking(1, 0), new Marking(1, OMEGA)), simulated.markings());
        assertEquals(10, simulated.runsWithoutNew());
        assertEquals(Set.copyOf(new ExactReachabilityAnalyzer(net).analyze().markings()),
                Set.copyOf(simulated.markings()));
    }

    @Test
    void unboundedQueueSaturatesAndEveryMarkingIsCoveredByTheTree() {
        PetriNet net = NetJson.parse(QUEUE);
        SimulatedReachability.Result simulated =
                new SimulatedReachability(new SimulationSeries(net, 1, 1000), 0.95).runUntilSaturated(50, 10_000);
        ReachabilityResult tree = new ExactReachabilityAnalyzer(net).analyze();
        ReachabilityComparison comparison = ReachabilityComparison.of(simulated, tree);

        assertEquals(50, simulated.runsWithoutNew(), "the set must saturate thanks to ω");
        assertTrue(simulated.markings().stream().anyMatch(Marking::hasOmega));
        assertFalse(tree.isBounded());
        assertTrue(comparison.notCovered().isEmpty(), "not covered: " + comparison.notCovered());
        assertTrue(comparison.inTree() > 0);
    }

    @Test
    void boundedNetNeverGetsOmegaInSimulation() {
        for (String file : List.of("lecture7-abc.json", "shared-resource.json")) {
            PetriNet net = NetJson.read(Path.of("examples", file));
            SimulatedReachability.Result simulated =
                    new SimulatedReachability(new SimulationSeries(net, 1, 1000), 0.95).runUntilSaturated(30, 10_000);
            ReachabilityComparison comparison =
                    ReachabilityComparison.of(simulated, new ExactReachabilityAnalyzer(net).analyze());

            assertFalse(simulated.markings().stream().anyMatch(Marking::hasOmega), file);
            assertEquals(comparison.treeMarkings(), comparison.inTree(), file);
            assertEquals(simulated.markings().size(), comparison.inTree(), file);
            assertEquals(comparison.deadInTree(), comparison.deadMet(), file);
        }
    }
}
