package ua.kpi.petri.analysis;

import org.junit.jupiter.api.Test;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.observer.SimulationListener;
import ua.kpi.petri.simulation.NaiveEnabledTransitionsFinder;
import ua.kpi.petri.simulation.Simulator;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.kpi.petri.model.Marking.OMEGA;

class ExactReachabilityAnalyzerTest {

    private static final String GENERATOR = """
            { "name": "gen", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" } ],
              "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } } ] }
            """;

    private static PetriNet example(String file) {
        return NetJson.read(Path.of("examples", file));
    }

    /** Lecture 7, slide 4: all markings of the classical column, including (0 0 0 1) after A, C. */
    @Test
    void lecture7Example() {
        ReachabilityResult result = new ExactReachabilityAnalyzer(example("lecture7-abc.json")).analyze();

        assertTrue(result.complete());
        assertEquals(Set.of(new Marking(1, 1, 0, 0), new Marking(0, 2, 0, 0), new Marking(1, 0, 1, 0),
                new Marking(0, 1, 1, 0), new Marking(0, 0, 0, 1), new Marking(0, 0, 2, 0)),
                Set.copyOf(result.markings()));
        assertEquals(new Marking(1, 1, 0, 0), result.markings().getFirst());
        assertEquals(Set.of(new Marking(0, 0, 0, 1), new Marking(0, 0, 2, 0)), Set.copyOf(result.terminal()));
        assertTrue(result.deadTransitions().isEmpty());
        assertTrue(result.isBounded());
        assertEquals(2, result.boundK());
        assertTrue(result.edges().contains(
                new ReachabilityResult.Edge(new Marking(0, 2, 0, 0), "C", new Marking(0, 0, 0, 1))));
    }

    @Test
    void sharedResourceHasOnePathAndOneDeadlock() {
        ReachabilityResult result = new ExactReachabilityAnalyzer(example("shared-resource.json")).analyze();

        assertEquals(7, result.markings().size());
        assertEquals(List.of(new Marking(0, 1, 0, 3)), result.terminal());
        assertEquals(6, result.edges().size());
        assertEquals(3, result.boundK());
    }

    @Test
    void generatorLoopGivesOmega() {
        ReachabilityResult result = new ExactReachabilityAnalyzer(NetJson.parse(GENERATOR)).analyze();

        assertTrue(result.complete());
        assertEquals(List.of(new Marking(1, 0), new Marking(1, OMEGA)), result.markings());
        assertFalse(result.isBounded());
        assertEquals(List.of("Queue"), result.unboundedPlaces());
        assertEquals(1, result.bound(result.net().findPlace("Gen").orElseThrow()));
        assertTrue(result.terminal().isEmpty());
        assertEquals("(1 ω)", result.markings().get(1).toString());
    }

    @Test
    void omegaAppearsInEveryPlaceThatGrows() {
        PetriNet net = NetJson.parse("""
                { "name": "two", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Q1" }, { "name": "Q2" } ],
                  "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Q1": 1, "Q2": 2 } } ] }
                """);
        ReachabilityResult result = new ExactReachabilityAnalyzer(net).analyze();

        assertEquals(List.of(new Marking(1, 0, 0), new Marking(1, OMEGA, OMEGA)), result.markings());
    }

    /** ω needs an ancestor on the path; a larger marking in another branch is not enough. */
    @Test
    void omegaOnlyFromAncestors() {
        PetriNet net = NetJson.parse("""
                { "name": "branches", "places": [ { "name": "S", "tokens": 1 }, { "name": "A" }, { "name": "B" } ],
                  "transitions": [ { "name": "ToA", "in": { "S": 1 }, "out": { "A": 1 } },
                                   { "name": "ToB", "in": { "S": 1 }, "out": { "B": 2 } } ] }
                """);
        ReachabilityResult result = new ExactReachabilityAnalyzer(net).analyze();

        assertTrue(result.isBounded());
        assertEquals(Set.of(new Marking(1, 0, 0), new Marking(0, 1, 0), new Marking(0, 0, 2)),
                Set.copyOf(result.markings()));
    }

    @Test
    void transitionThatNeedsMoreTokensThanEverAvailableIsDead() {
        PetriNet net = NetJson.parse("""
                { "name": "dead", "places": [ { "name": "P", "tokens": 1 }, { "name": "Q" } ],
                  "transitions": [ { "name": "Move", "in": { "P": 1 }, "out": { "Q": 1 } },
                                   { "name": "Never", "in": { "P": 2 }, "out": { "Q": 1 } } ] }
                """);
        ReachabilityResult result = new ExactReachabilityAnalyzer(net).analyze();

        assertEquals(List.of("Never"), result.deadTransitions());
    }

    @Test
    void nodeLimitMarksResultIncomplete() {
        PetriNet net = NetJson.parse("""
                { "name": "big", "places": [ { "name": "P", "tokens": 1000 }, { "name": "Q" } ],
                  "transitions": [ { "name": "Move", "in": { "P": 1 }, "out": { "Q": 1 } } ] }
                """);
        ReachabilityResult limited = new ExactReachabilityAnalyzer(net, 50).analyze();
        ReachabilityResult full = new ExactReachabilityAnalyzer(net).analyze();

        assertFalse(limited.complete());
        assertEquals(50, limited.nodes());
        assertTrue(full.complete());
        assertEquals(1001, full.markings().size());
    }

    /** Verification preview: markings met in simulation runs are exactly the reachable markings. */
    @Test
    void simulationVisitsExactlyTheReachableMarkings() {
        for (String file : List.of("lecture7-abc.json", "shared-resource.json")) {
            PetriNet net = example(file);
            Set<Marking> exact = Set.copyOf(new ExactReachabilityAnalyzer(net).analyze().markings());

            Set<Marking> simulated = new HashSet<>();
            RandomGenerator rng = RandomGeneratorFactory.of("L64X128MixRandom").create(1);
            Simulator simulator = new Simulator(net, new NaiveEnabledTransitionsFinder(net), rng)
                    .addListener(new SimulationListener() {
                        @Override
                        public void onStart(PetriNet n) {
                            simulated.add(n.snapshot());
                        }

                        @Override
                        public void onTickEnd(int tick) {
                            simulated.add(net.snapshot());
                        }
                    });
            for (int run = 0; run < 200; run++) {
                simulator.run(1000);
            }
            assertEquals(exact, simulated, file);
        }
    }
}
