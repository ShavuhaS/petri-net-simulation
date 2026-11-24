package ua.kpi.petri.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PetriNetTest {

    private PetriNet net() {
        Place a = new Place("A", 2);
        Place b = new Place("B", 0);
        return new PetriNet("net", List.of(a, b),
                List.of(new Transition("T", List.of(new Arc(a, 1)), List.of(new Arc(b, 1)))));
    }

    @Test
    void snapshotFollowsPlaceOrder() {
        PetriNet net = net();
        net.getTransitions().getFirst().fire();
        assertEquals(new Marking(1, 1), net.snapshot());
        assertEquals(new Marking(2, 0), net.initialMarking());
    }

    @Test
    void resetAndRestore() {
        PetriNet net = net();
        Marking initial = net.snapshot();
        net.getTransitions().getFirst().fire();
        Marking afterFire = net.snapshot();

        net.reset();
        assertEquals(initial, net.snapshot());
        net.restore(afterFire);
        assertEquals(afterFire, net.snapshot());
    }

    @Test
    void markingIsValueObject() {
        int[] tokens = {1, 2, 3};
        Marking marking = new Marking(tokens);
        tokens[0] = 99;

        assertEquals(1, marking.get(0));
        assertEquals(new Marking(1, 2, 3), marking);
        assertNotEquals(new Marking(1, 2, 4), marking);
        Set<Marking> set = new HashSet<>(List.of(marking, new Marking(1, 2, 3)));
        assertEquals(1, set.size());
        assertEquals("(1 2 3)", marking.toString());
    }

    @Test
    void rejectsDuplicateNamesAndForeignPlaces() {
        Place a = new Place("A", 0);
        Transition loop = new Transition("T", List.of(new Arc(a, 1)), List.of(new Arc(a, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new PetriNet("n", List.of(a, new Place("A", 1)), List.of(loop)));

        Place foreign = new Place("X", 0);
        assertThrows(IllegalArgumentException.class, () -> new PetriNet("n", List.of(a),
                List.of(new Transition("T", List.of(new Arc(foreign, 1)), List.of(new Arc(a, 1))))));
    }

    @Test
    void requiresAtLeastOneTransition() {
        assertThrows(IllegalArgumentException.class,
                () -> new PetriNet("n", List.of(new Place("A", 1)), List.of()));
    }
}
