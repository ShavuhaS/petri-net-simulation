package ua.kpi.petri.io;

import java.util.List;
import java.util.Map;

/**
 * JSON shape of a Petri net: for each transition, its input and output places with arc weights.
 */
public record NetDefinition(String name, List<PlaceDefinition> places, List<TransitionDefinition> transitions) {

    /** Omitted {@code tokens} means an empty place. */
    public record PlaceDefinition(String name, Integer tokens) {
    }

    public record TransitionDefinition(String name, Map<String, Integer> in, Map<String, Integer> out) {
    }
}
