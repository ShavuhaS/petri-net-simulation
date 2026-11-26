package ua.kpi.petri.simulation;

import org.junit.jupiter.api.Test;
import ua.kpi.petri.generator.NetGenerators;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.observer.FiringCounter;
import ua.kpi.petri.observer.SimulationListener;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalEnabledTransitionsFinderTest {

    private static List<PetriNet> nets() {
        return List.of(
                NetJson.read(Path.of("examples/lecture7-abc.json")),
                NetJson.read(Path.of("examples/shared-resource.json")),
                NetGenerators.stateMachine(10, 60, 5),
                NetGenerators.queueChain(5, 2),
                NetGenerators.philosophers(4, false),
                NetGenerators.philosophers(5, true));
    }

    /** The run is driven by the incremental finder; after every firing its set is compared with a full check. */
    @Test
    void enabledSetMatchesFullCheckAfterEveryFiring() {
        for (PetriNet net : nets()) {
            IncrementalEnabledTransitionsFinder incremental = new IncrementalEnabledTransitionsFinder(net);
            NaiveEnabledTransitionsFinder naive = new NaiveEnabledTransitionsFinder(net);
            SimulationListener check = new SimulationListener() {
                @Override
                public void onStart(PetriNet n) {
                    assertEquals(Set.copyOf(naive.findEnabled()), Set.copyOf(incremental.findEnabled()), net.getName());
                }

                @Override
                public void onFire(int tick, Transition transition) {
                    List<Transition> enabled = incremental.findEnabled();
                    assertEquals(enabled.size(), new HashSet<>(enabled).size(), "no duplicates");
                    assertEquals(Set.copyOf(naive.findEnabled()), Set.copyOf(enabled), net.getName() + " tick " + tick);
                }
            };
            Simulator simulator = new Simulator(net, incremental, SimulationSeries.newRandom(1)).addListener(check);
            for (int run = 0; run < 20; run++) {
                simulator.run(2000);
            }
        }
    }

    /** Same chain as in SimulatorTest: whatever the order of the set, each enabled transition has chance 1/3. */
    @Test
    void everyEnabledTransitionHasEqualChance() {
        String chain = """
                { "name": "chain", "places": [ { "name": "A", "tokens": 1 }, { "name": "B", "tokens": 1 }, { "name": "X" } ],
                  "transitions": [ { "name": "T1", "in": { "A": 1 }, "out": { "X": 1 } },
                                   { "name": "T2", "in": { "A": 1, "B": 1 }, "out": { "X": 1 } },
                                   { "name": "T3", "in": { "B": 1 }, "out": { "X": 1 } } ] }
                """;
        PetriNet net = NetJson.parse(chain);
        int runs = 30_000;
        FiringCounter counter = new FiringCounter();
        RandomGenerator rng = SimulationSeries.newRandom(3);
        long[] counts = new long[3];
        Simulator simulator = new Simulator(net, new IncrementalEnabledTransitionsFinder(net), rng).addListener(counter);
        for (int i = 0; i < runs; i++) {
            simulator.run(1);
            for (int t = 0; t < 3; t++) {
                counts[t] += counter.count(net.getTransitions().get(t));
            }
        }
        for (long count : counts) {
            assertTrue(Math.abs((double) count / runs - 1.0 / 3) < 0.015, "share = " + (double) count / runs);
        }
    }

    /** Long runs of a state machine: both finders give the same mean firing counts within statistical error. */
    @Test
    void firingFrequenciesAgreeWithNaive() {
        PetriNet net = NetGenerators.stateMachine(5, 15, 2);
        int ticks = 200_000;
        double[] naive = shares(net, FinderType.NAIVE, ticks);
        double[] incremental = shares(net, FinderType.INCREMENTAL, ticks);
        for (int t = 0; t < naive.length; t++) {
            assertEquals(naive[t], incremental[t], 0.01, net.getTransitions().get(t).getName());
        }
    }

    private static double[] shares(PetriNet net, FinderType type, int ticks) {
        FiringCounter counter = new FiringCounter();
        new Simulator(net, type.create(net), SimulationSeries.newRandom(7)).addListener(counter).run(ticks);
        double[] shares = new double[net.getTransitions().size()];
        for (Transition t : net.getTransitions()) {
            shares[t.getIndex()] = (double) counter.count(t) / ticks;
        }
        return shares;
    }
}
