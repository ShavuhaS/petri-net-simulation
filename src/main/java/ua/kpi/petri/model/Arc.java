package ua.kpi.petri.model;

import java.util.Objects;

public record Arc(Place place, int weight) {

    public Arc {
        Objects.requireNonNull(place, "place");
        if (weight < 1) {
            throw new IllegalArgumentException(
                    "Arc weight must be at least 1, got " + weight + " for place '" + place.getName() + "'");
        }
    }

    @Override
    public String toString() {
        return weight == 1 ? place.getName() : place.getName() + "*" + weight;
    }
}
