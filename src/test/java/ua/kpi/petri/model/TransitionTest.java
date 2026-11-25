package ua.kpi.petri.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitionTest {

    private final Place p1 = new Place("P1", 3);
    private final Place p2 = new Place("P2", 0);
    private final Transition t = new Transition("T", List.of(new Arc(p1, 2)), List.of(new Arc(p2, 3)));

    @Test
    void enabledOnlyWhenEnoughTokensForArcWeight() {
        assertTrue(t.isEnabled());
        p1.setTokens(1);
        assertFalse(t.isEnabled());
    }

    @Test
    void fireConsumesAndProducesByArcWeights() {
        t.fire();
        assertEquals(1, p1.getTokens());
        assertEquals(3, p2.getTokens());
    }

    @Test
    void firingDisabledTransitionFails() {
        t.fire();
        assertThrows(IllegalStateException.class, t::fire);
        assertEquals(1, p1.getTokens());
        assertEquals(3, p2.getTokens());
    }

    @Test
    void generatorLoopProducesTokensForever() {
        Place generator = new Place("Gen", 1);
        Transition arrival = new Transition("Arrival",
                List.of(new Arc(generator, 1)), List.of(new Arc(generator, 1), new Arc(p2, 1)));
        arrival.fire();
        arrival.fire();
        assertTrue(arrival.isEnabled());
        assertEquals(2, p2.getTokens());
    }

    @Test
    void requiresInputAndOutputPlaces() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transition("Source", List.of(), List.of(new Arc(p2, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> new Transition("Sink", List.of(new Arc(p1, 1)), List.of()));
    }

    @Test
    void selfLoopKeepsTokenCount() {
        Transition loop = new Transition("Loop", List.of(new Arc(p1, 1)), List.of(new Arc(p1, 1)));
        loop.fire();
        assertEquals(3, p1.getTokens());
    }

    @Test
    void firingAMarkingMatchesFiringTheNetAndLeavesNetUnchanged() {
        PetriNet net = new PetriNet("n", List.of(p1, p2), List.of(t));
        Marking before = net.snapshot();

        assertTrue(t.isEnabledIn(before));
        Marking after = t.fire(before);
        assertEquals(before, net.snapshot());

        t.fire();
        assertEquals(net.snapshot(), after);
        assertFalse(t.isEnabledIn(after));
        assertThrows(IllegalStateException.class, () -> t.fire(after));
    }

    @Test
    void omegaStaysOmegaWhenFiringAMarking() {
        new PetriNet("n", List.of(p1, p2), List.of(t));
        Marking unbounded = new Marking(Marking.OMEGA, 0);

        assertTrue(t.isEnabledIn(unbounded));
        assertEquals(new Marking(Marking.OMEGA, 3), t.fire(unbounded));
        assertEquals(new Marking(Marking.OMEGA, Marking.OMEGA), t.fire(new Marking(Marking.OMEGA, Marking.OMEGA)));
    }

    @Test
    void rejectsInvalidStructure() {
        assertThrows(IllegalArgumentException.class, () -> new Arc(p1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Place("P", -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Transition("T", List.of(new Arc(p1, 1), new Arc(p1, 2)), List.of(new Arc(p2, 1))));
    }
}
