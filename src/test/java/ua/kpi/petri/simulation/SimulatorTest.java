package ua.kpi.petri.simulation;

import org.junit.jupiter.api.Test;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.observer.FiringCounter;
import ua.kpi.petri.observer.SimulationListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorTest {

    private static RandomGenerator random(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    private static PetriNet lecture7() {
        return NetJson.read(Path.of("examples/lecture7-abc.json"));
    }

    private static SimulationResult run(PetriNet net, RandomGenerator rng, int maxTicks, SimulationListener... listeners) {
        Simulator simulator = new Simulator(net, new NaiveEnabledTransitionsFinder(net), rng);
        for (SimulationListener listener : listeners) {
            simulator.addListener(listener);
        }
        return simulator.run(maxTicks);
    }

    /**
     * Lecture 7, slides 3-4 (classical net column): A or B first; after A, B and C conflict,
     * so C fires in 1/4 of runs and the net stops in (0 0 0 1) at tick 2, otherwise in (0 0 2 0) at tick 3.
     */
    @Test
    void lecture7ExampleFollowsSlides3And4() {
        int runs = 20_000;
        int cFired = 0;
        RandomGenerator rng = random(11);
        for (int i = 0; i < runs; i++) {
            SimulationResult result = run(lecture7(), rng, 100);

            assertEquals(StopReason.DEADLOCK, result.stopReason());
            if (result.finalMarking().equals(new Marking(0, 0, 0, 1))) {
                assertEquals(2, result.ticks());
                cFired++;
            } else {
                assertEquals(new Marking(0, 0, 2, 0), result.finalMarking());
                assertEquals(3, result.ticks());
            }
        }
        double share = (double) cFired / runs;
        assertTrue(Math.abs(share - 0.25) < 0.015, "share of runs where C fired = " + share);
    }

    /** Every enabled transition has the same chance in each tick, whatever the conflict structure. */
    @Test
    void everyEnabledTransitionHasEqualChance() {
        String chain = """
                { "name": "chain", "places": [ { "name": "A", "tokens": 1 }, { "name": "B", "tokens": 1 }, { "name": "X" } ],
                  "transitions": [ { "name": "T1", "in": { "A": 1 }, "out": { "X": 1 } },
                                   { "name": "T2", "in": { "A": 1, "B": 1 }, "out": { "X": 1 } },
                                   { "name": "T3", "in": { "B": 1 }, "out": { "X": 1 } } ] }
                """;
        int runs = 30_000;
        long[] counts = new long[3];
        RandomGenerator rng = random(3);
        for (int i = 0; i < runs; i++) {
            PetriNet net = NetJson.parse(chain);
            FiringCounter counter = new FiringCounter();
            run(net, rng, 1, counter);
            for (int t = 0; t < 3; t++) {
                counts[t] += counter.count(net.getTransitions().get(t));
            }
        }
        for (long count : counts) {
            assertTrue(Math.abs((double) count / runs - 1.0 / 3) < 0.015, "share = " + (double) count / runs);
        }
    }

    @Test
    void oneFiringPerTick() {
        PetriNet net = NetJson.parse("""
                { "name": "n", "places": [ { "name": "A", "tokens": 2 }, { "name": "B", "tokens": 1 }, { "name": "X" } ],
                  "transitions": [ { "name": "TA", "in": { "A": 1 }, "out": { "X": 1 } },
                                   { "name": "TB", "in": { "B": 1 }, "out": { "X": 1 } } ] }
                """);
        List<Integer> firingTicks = new ArrayList<>();
        SimulationResult result = run(net, random(1), 100, new SimulationListener() {
            @Override
            public void onFire(int tick, Transition transition) {
                firingTicks.add(tick);
            }
        });

        assertEquals(3, result.ticks());
        assertEquals(List.of(1, 2, 3), firingTicks);
        assertEquals(new Marking(0, 0, 3), result.finalMarking());
    }

    @Test
    void stopsAtTickLimit() {
        PetriNet net = NetJson.parse("""
                { "name": "gen", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" } ],
                  "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } } ] }
                """);
        SimulationResult result = run(net, random(1), 10);

        assertEquals(StopReason.MAX_TICKS, result.stopReason());
        assertEquals(10, result.ticks());
        assertEquals(new Marking(1, 10), result.finalMarking());
    }

    @Test
    void sameSeedGivesSameRunAndRunsStartFromInitialMarking() {
        PetriNet net = lecture7();
        assertEquals(protocolOf(net, 42), protocolOf(net, 42));

        Simulator simulator = new Simulator(net, new NaiveEnabledTransitionsFinder(net), random(5));
        simulator.run(1);
        assertEquals(net.initialMarking(), new Simulator(net, new NaiveEnabledTransitionsFinder(net), random(5))
                .run(0).finalMarking());
    }

    @Test
    void zeroTicksDoesNothing() {
        PetriNet net = lecture7();
        SimulationResult result = run(net, random(1), 0);
        assertEquals(0, result.ticks());
        assertEquals(net.initialMarking(), result.finalMarking());
    }

    private static List<String> protocolOf(PetriNet net, long seed) {
        List<String> events = new ArrayList<>();
        run(net, random(seed), 100, new SimulationListener() {
            @Override
            public void onFire(int tick, Transition transition) {
                events.add(tick + ":" + transition.getName());
            }
        });
        return events;
    }
}
