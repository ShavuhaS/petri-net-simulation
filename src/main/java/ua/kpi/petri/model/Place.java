package ua.kpi.petri.model;

import java.util.Objects;

public final class Place {

    private final String name;
    private final int initialTokens;
    private int tokens;

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
