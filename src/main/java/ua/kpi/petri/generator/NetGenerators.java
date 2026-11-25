package ua.kpi.petri.generator;

import ua.kpi.petri.model.Arc;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.simulation.SimulationSeries;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class NetGenerators {

    private NetGenerators() {
    }

    /**
     * State machine: every transition has one input and one output place, each place holds one token.
     * Transitions T1..TP form the cycle P1 -> P2 -> … -> PP -> P1, so the net is strongly connected and never
     * deadlocks; the remaining T − P transitions join a random place to a different random place.
     */
    public static PetriNet stateMachine(int places, int transitions, long seed) {
        require(places >= 2, "A state machine needs at least 2 places, got " + places);
        require(transitions >= places,
                "A state machine needs at least as many transitions as places (" + places + "), got " + transitions);
        List<Place> p = new ArrayList<>(places);
        for (int i = 1; i <= places; i++) {
            p.add(new Place("P" + i, 1));
        }
        List<Transition> t = new ArrayList<>(transitions);
        for (int i = 0; i < places; i++) {
            t.add(move("T" + (i + 1), p.get(i), p.get((i + 1) % places)));
        }
        RandomGenerator random = SimulationSeries.newRandom(seed);
        for (int i = places; i < transitions; i++) {
            int from = random.nextInt(places);
            int to = random.nextInt(places - 1);
            if (to >= from) {
                to++;
            }
            t.add(move("T" + (i + 1), p.get(from), p.get(to)));
        }
        return new PetriNet("state-machine-P" + places + "-T" + transitions + "-seed" + seed, p, t);
    }

    /**
     * Chain of n queueing nodes with c servers each. A generator loop puts customers into the first queue;
     * a customer served by node i goes to the queue of node i + 1, after the last node to Served.
     * Node i: Start_i takes a customer from Queue_i and a free server, Finish_i returns the server.
     * P = 3n + 2, T = 2n + 1; the queues grow without limit, so the net never deadlocks.
     */
    public static PetriNet queueChain(int nodes, int servers) {
        require(nodes >= 1, "A queue chain needs at least 1 node, got " + nodes);
        require(servers >= 1, "Each node needs at least 1 server, got " + servers);
        Place generator = new Place("Gen", 1);
        List<Place> places = new ArrayList<>(List.of(generator));
        Place queue = new Place("Queue1", 0);
        List<Transition> transitions = new ArrayList<>(List.of(new Transition("Arrival",
                List.of(new Arc(generator, 1)), List.of(new Arc(generator, 1), new Arc(queue, 1)))));
        for (int i = 1; i <= nodes; i++) {
            Place free = new Place("Free" + i, servers);
            Place busy = new Place("Busy" + i, 0);
            Place next = new Place(i < nodes ? "Queue" + (i + 1) : "Served", 0);
            places.addAll(List.of(queue, free, busy));
            transitions.add(new Transition("Start" + i,
                    List.of(new Arc(queue, 1), new Arc(free, 1)), List.of(new Arc(busy, 1))));
            transitions.add(new Transition("Finish" + i,
                    List.of(new Arc(busy, 1)), List.of(new Arc(free, 1), new Arc(next, 1))));
            queue = next;
        }
        places.add(queue);
        return new PetriNet("queue-chain-n" + nodes + "-c" + servers, places, transitions);
    }

    /**
     * Dining philosophers: philosopher i thinks, takes the sticks i and i + 1 (modulo n), eats and puts both back.
     * With {@code bothAtOnce} both sticks are taken by one transition and the net never deadlocks;
     * otherwise the left stick is taken first, and the net deadlocks once every philosopher holds the left one.
     */
    public static PetriNet philosophers(int count, boolean bothAtOnce) {
        require(count >= 2, "There must be at least 2 philosophers, got " + count);
        List<Place> think = new ArrayList<>(count);
        List<Place> sticks = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            think.add(new Place("Think" + i, 1));
            sticks.add(new Place("Stick" + i, 1));
        }
        List<Place> places = new ArrayList<>();
        List<Transition> transitions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int n = i + 1;
            Place left = sticks.get(i);
            Place right = sticks.get((i + 1) % count);
            Place eat = new Place("Eat" + n, 0);
            places.addAll(List.of(think.get(i), left));
            if (bothAtOnce) {
                transitions.add(new Transition("Take" + n,
                        List.of(new Arc(think.get(i), 1), new Arc(left, 1), new Arc(right, 1)),
                        List.of(new Arc(eat, 1))));
            } else {
                Place hasLeft = new Place("HasLeft" + n, 0);
                places.add(hasLeft);
                transitions.add(new Transition("TakeLeft" + n,
                        List.of(new Arc(think.get(i), 1), new Arc(left, 1)), List.of(new Arc(hasLeft, 1))));
                transitions.add(new Transition("TakeRight" + n,
                        List.of(new Arc(hasLeft, 1), new Arc(right, 1)), List.of(new Arc(eat, 1))));
            }
            places.add(eat);
            transitions.add(new Transition("Put" + n, List.of(new Arc(eat, 1)),
                    List.of(new Arc(think.get(i), 1), new Arc(left, 1), new Arc(right, 1))));
        }
        return new PetriNet("philosophers-" + count + (bothAtOnce ? "-both-at-once" : ""), places, transitions);
    }

    private static Transition move(String name, Place from, Place to) {
        return new Transition(name, List.of(new Arc(from, 1)), List.of(new Arc(to, 1)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
