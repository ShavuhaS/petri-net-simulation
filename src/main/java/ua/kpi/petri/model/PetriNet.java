package ua.kpi.petri.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PetriNet {

    private final String name;
    private final List<Place> places;
    private final List<Transition> transitions;
    private final Map<String, Place> placesByName = new HashMap<>();
    private final Map<String, Transition> transitionsByName = new HashMap<>();

    public PetriNet(String name, List<Place> places, List<Transition> transitions) {
        this.name = Place.requireName(name);
        this.places = List.copyOf(places);
        this.transitions = List.copyOf(transitions);

        for (Place place : this.places) {
            if (placesByName.putIfAbsent(place.getName(), place) != null) {
                throw new IllegalArgumentException("Duplicate place name: '" + place.getName() + "'");
            }
        }
        if (this.transitions.isEmpty()) {
            throw new IllegalArgumentException("Net '" + name + "' must have at least one transition");
        }
        for (Transition transition : this.transitions) {
            if (transitionsByName.putIfAbsent(transition.getName(), transition) != null) {
                throw new IllegalArgumentException("Duplicate transition name: '" + transition.getName() + "'");
            }
        }
        requireArcsWithinNet();
        for (Transition transition : this.transitions) {
            if (transition.isIndexAssigned()) {
                throw new IllegalArgumentException(
                        "Transition '" + transition.getName() + "' already belongs to another net");
            }
        }
        for (int i = 0; i < this.transitions.size(); i++) {
            this.transitions.get(i).assignIndex(i);
        }
    }

    public String getName() {
        return name;
    }

    public List<Place> getPlaces() {
        return places;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public Optional<Place> findPlace(String placeName) {
        return Optional.ofNullable(placesByName.get(placeName));
    }

    public Optional<Transition> findTransition(String transitionName) {
        return Optional.ofNullable(transitionsByName.get(transitionName));
    }

    public Marking snapshot() {
        int[] tokens = new int[places.size()];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = places.get(i).getTokens();
        }
        return new Marking(tokens);
    }

    public Marking initialMarking() {
        int[] tokens = new int[places.size()];
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = places.get(i).getInitialTokens();
        }
        return new Marking(tokens);
    }

    public void restore(Marking marking) {
        if (marking.size() != places.size()) {
            throw new IllegalArgumentException(
                    "Marking has " + marking.size() + " places, net has " + places.size());
        }
        for (int i = 0; i < places.size(); i++) {
            places.get(i).setTokens(marking.get(i));
        }
    }

    public void reset() {
        places.forEach(Place::reset);
    }

    private void requireArcsWithinNet() {
        Set<Place> own = Collections.newSetFromMap(new IdentityHashMap<>());
        own.addAll(places);
        for (Transition transition : transitions) {
            for (List<Arc> arcs : List.of(transition.getInputs(), transition.getOutputs())) {
                for (Arc arc : arcs) {
                    if (!own.contains(arc.place())) {
                        throw new IllegalArgumentException("Transition '" + transition.getName()
                                + "' references place '" + arc.place().getName() + "' outside the net");
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return "PetriNet '" + name + "' (" + places.size() + " places, " + transitions.size() + " transitions)";
    }
}
