package ua.kpi.petri.generator;

import org.junit.jupiter.api.Test;
import ua.kpi.petri.analysis.ExactReachabilityAnalyzer;
import ua.kpi.petri.analysis.ReachabilityResult;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.SimulationSeries;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetGeneratorsTest {

    private static int index(PetriNet net, String place) {
        return net.findPlace(place).orElseThrow().getIndex();
    }

    @Test
    void stateMachineHasCycleAndRandomMovesBetweenDifferentPlaces() {
        PetriNet net = NetGenerators.stateMachine(10, 50, 7);

        assertEquals(10, net.getPlaces().size());
        assertEquals(50, net.getTransitions().size());
        assertTrue(net.getPlaces().stream().allMatch(p -> p.getInitialTokens() == 1));
        for (Transition t : net.getTransitions()) {
            assertEquals(1, t.getInputs().size());
            assertEquals(1, t.getOutputs().size());
            assertEquals(1, t.getInputs().getFirst().weight());
            assertEquals(1, t.getOutputs().getFirst().weight());
            assertNotEquals(t.getInputs().getFirst().place(), t.getOutputs().getFirst().place(), t.toString());
        }
        for (int i = 0; i < 10; i++) {
            Transition t = net.getTransitions().get(i);
            assertEquals(i, t.getInputs().getFirst().place().getIndex());
            assertEquals((i + 1) % 10, t.getOutputs().getFirst().place().getIndex());
        }
    }

    @Test
    void stateMachineDependsOnlyOnSeed() {
        String first = NetJson.toJson(NetGenerators.stateMachine(20, 100, 3));

        assertEquals(first, NetJson.toJson(NetGenerators.stateMachine(20, 100, 3)));
        assertNotEquals(first, NetJson.toJson(NetGenerators.stateMachine(20, 100, 4)));
    }

    @Test
    void stateMachineNeverDeadlocksAndKeepsTokenCount() {
        PetriNet net = NetGenerators.stateMachine(5, 40, 11);
        SimulationSeries series = new SimulationSeries(net, 1, 10_000);

        for (int run = 0; run < 10; run++) {
            SimulationResult result = series.run(run);
            assertFalse(result.deadlocked());
            assertEquals(5, Arrays.stream(result.finalMarking().toArray()).sum());
        }
    }

    @Test
    void stateMachineRejectsTooFewPlacesOrTransitions() {
        assertThrows(IllegalArgumentException.class, () -> NetGenerators.stateMachine(1, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> NetGenerators.stateMachine(10, 9, 1));
    }

    @Test
    void queueChainHasExpectedSizeAndKeepsServersOfEveryNode() {
        PetriNet net = NetGenerators.queueChain(3, 2);

        assertEquals(3 * 3 + 2, net.getPlaces().size());
        assertEquals(2 * 3 + 1, net.getTransitions().size());
        assertEquals(List.of("Gen", "Queue1", "Free1", "Busy1", "Queue2", "Free2", "Busy2",
                "Queue3", "Free3", "Busy3", "Served"), net.getPlaces().stream().map(Place::getName).toList());

        ReachabilityResult tree = new ExactReachabilityAnalyzer(net).analyze();
        assertTrue(tree.complete());
        assertTrue(tree.terminal().isEmpty());
        assertEquals(List.of("Queue1", "Queue2", "Queue3", "Served"), tree.unboundedPlaces());
        for (int node = 1; node <= 3; node++) {
            int free = index(net, "Free" + node);
            int busy = index(net, "Busy" + node);
            for (Marking marking : tree.markings()) {
                assertEquals(2, marking.get(free) + marking.get(busy), marking.toString());
            }
        }
    }

    @Test
    void philosophersTakingLeftStickFirstDeadlockOnlyWhenAllHoldTheLeftStick() {
        PetriNet net = NetGenerators.philosophers(3, false);

        assertEquals(12, net.getPlaces().size());
        assertEquals(9, net.getTransitions().size());
        ReachabilityResult tree = new ExactReachabilityAnalyzer(net).analyze();
        assertTrue(tree.isBounded());
        assertEquals(1, tree.terminal().size());
        Marking dead = tree.terminal().getFirst();
        for (Place place : net.getPlaces()) {
            int expected = place.getName().startsWith("HasLeft") ? 1 : 0;
            assertEquals(expected, dead.get(place.getIndex()), place.getName());
        }
    }

    @Test
    void philosophersTakingBothSticksNeverDeadlockAndNeighboursNeverEatTogether() {
        PetriNet net = NetGenerators.philosophers(5, true);

        assertEquals(15, net.getPlaces().size());
        assertEquals(10, net.getTransitions().size());
        ReachabilityResult tree = new ExactReachabilityAnalyzer(net).analyze();
        assertTrue(tree.terminal().isEmpty());
        assertTrue(tree.deadTransitions().isEmpty());
        int mostEating = 0;
        for (Marking marking : tree.markings()) {
            int eating = 0;
            for (int i = 1; i <= 5; i++) {
                int here = marking.get(index(net, "Eat" + i));
                int next = marking.get(index(net, "Eat" + (i % 5 + 1)));
                assertTrue(here + next <= 1, marking.toString());
                eating += here;
            }
            mostEating = Math.max(mostEating, eating);
        }
        assertEquals(2, mostEating);
    }

    @Test
    void generatedNetsSurviveJsonRoundTrip() {
        for (PetriNet net : List.of(NetGenerators.stateMachine(4, 8, 1), NetGenerators.queueChain(2, 1),
                NetGenerators.philosophers(3, false))) {
            String json = NetJson.toJson(net);
            assertEquals(json, NetJson.toJson(NetJson.parse(json)));
        }
    }
}
