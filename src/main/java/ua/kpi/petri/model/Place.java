package ua.kpi.petri.model;

import java.util.Objects;

public final class Place {

    private static final int UNASSIGNED = -1;

    private final String name;
    private final int initialTokens;
    private int tokens;
    private int index = UNASSIGNED;

    public Place(String name, int initialTokens) {
        this.name = requireName(name);
        if (initialTokens < 0) {
            throw new IllegalArgumentException("Place '" + name + "' has negative tokens: " + initialTokens);
        }
        this.initialTokens = initialTokens;
        this.tokens = initialTokens;
    }

    public String getName() {
        return name;
    }

    /** Position of this place in {@link PetriNet#getPlaces()} and in every {@link Marking} of the net. */
    public int getIndex() {
        if (index == UNASSIGNED) {
            throw new IllegalStateException("Place '" + name + "' does not belong to a net");
        }
        return index;
    }

    boolean isIndexAssigned() {
        return index != UNASSIGNED;
    }

    void assignIndex(int index) {
        this.index = index;
    }

    public int getInitialTokens() {
        return initialTokens;
    }

    public int getTokens() {
        return tokens;
    }

    void addTokens(int count) {
        tokens += count;
    }

    void removeTokens(int count) {
        if (count > tokens) {
            throw new IllegalStateException(
                    "Place '" + name + "' has " + tokens + " tokens, cannot remove " + count);
        }
        tokens -= count;
    }

    void setTokens(int tokens) {
        this.tokens = tokens;
    }

    void reset() {
        tokens = initialTokens;
    }

    static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        return name;
    }

    @Override
    public String toString() {
        return name + "(" + tokens + ")";
    }
}
