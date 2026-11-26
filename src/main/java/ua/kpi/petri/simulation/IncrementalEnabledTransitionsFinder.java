package ua.kpi.petri.simulation;

import ua.kpi.petri.model.Arc;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.model.Transition;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;

/**
 * Keeps the set of enabled transitions between firings. A firing changes only its input and output places,
 * so only the transitions that take tokens from those places are checked again.
 * <p>
 * The set is an array with the position of every transition in it, so adding, removing and taking the i-th
 * element are O(1). Removal moves the last element into the freed slot, so the order of the set differs from
 * the order of transitions in the net: with the same seed the run differs from {@link NaiveEnabledTransitionsFinder},
 * while every enabled transition still has the same chance to be chosen.
 */
public final class IncrementalEnabledTransitionsFinder implements EnabledTransitionsFinder {

    private static final int ABSENT = -1;

    private final List<Transition> transitions;
    /** For each transition, the transitions whose firing condition its firing can change. */
    private final Transition[][] affected;
    private final Transition[] enabled;
    private final int[] position;
    private int size;
    private final List<Transition> view = new EnabledView();

    public IncrementalEnabledTransitionsFinder(PetriNet net) {
        this.transitions = net.getTransitions();
        this.enabled = new Transition[transitions.size()];
        this.position = new int[transitions.size()];
        this.affected = affectedTransitions(net);
        reset();
    }

    @Override
    public List<Transition> findEnabled() {
        return view;
    }

    @Override
    public void onFired(Transition transition) {
        for (Transition t : affected[transition.getIndex()]) {
            update(t);
        }
    }

    @Override
    public void reset() {
        size = 0;
        Arrays.fill(position, ABSENT);
        for (Transition transition : transitions) {
            update(transition);
        }
    }

    private void update(Transition transition) {
        int index = transition.getIndex();
        boolean present = position[index] != ABSENT;
        if (transition.isEnabled()) {
            if (!present) {
                enabled[size] = transition;
                position[index] = size;
                size++;
            }
        } else if (present) {
            int slot = position[index];
            size--;
            Transition last = enabled[size];
            enabled[slot] = last;
            position[last.getIndex()] = slot;
            enabled[size] = null;
            position[index] = ABSENT;
        }
    }

    private static Transition[][] affectedTransitions(PetriNet net) {
        List<List<Transition>> readers = new ArrayList<>(net.getPlaces().size());
        for (Place _ : net.getPlaces()) {
            readers.add(new ArrayList<>());
        }
        for (Transition transition : net.getTransitions()) {
            for (Arc arc : transition.getInputs()) {
                readers.get(arc.place().getIndex()).add(transition);
            }
        }
        Transition[][] affected = new Transition[net.getTransitions().size()][];
        for (Transition transition : net.getTransitions()) {
            Set<Transition> set = new LinkedHashSet<>();
            for (List<Arc> arcs : List.of(transition.getInputs(), transition.getOutputs())) {
                for (Arc arc : arcs) {
                    set.addAll(readers.get(arc.place().getIndex()));
                }
            }
            affected[transition.getIndex()] = set.toArray(Transition[]::new);
        }
        return affected;
    }

    private final class EnabledView extends AbstractList<Transition> implements RandomAccess {

        @Override
        public Transition get(int i) {
            return enabled[Objects.checkIndex(i, size)];
        }

        @Override
        public int size() {
            return size;
        }
    }
}
