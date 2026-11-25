package ua.kpi.petri.model;

import java.util.Arrays;

/**
 * Immutable snapshot of token counts, ordered as the places of the net.
 * A place may hold {@link #OMEGA}: an unbounded number of tokens (reachability tree).
 * Safe to use as a HashSet/HashMap key.
 */
public final class Marking {

    /** Unbounded token count; larger than any real count, so comparisons need no special case. */
    public static final int OMEGA = Integer.MAX_VALUE;

    private final int[] tokens;
    private final int hash;

    public Marking(int... tokens) {
        this.tokens = tokens.clone();
        for (int count : this.tokens) {
            if (count < 0) {
                throw new IllegalArgumentException("Negative token count in marking: " + Arrays.toString(tokens));
            }
        }
        this.hash = Arrays.hashCode(this.tokens);
    }

    public int get(int placeIndex) {
        return tokens[placeIndex];
    }

    public boolean isOmega(int placeIndex) {
        return tokens[placeIndex] == OMEGA;
    }

    public boolean hasOmega() {
        for (int count : tokens) {
            if (count == OMEGA) {
                return true;
            }
        }
        return false;
    }

    /** True if every place of this marking holds at most as many tokens as in {@code other}; ω covers any count. */
    public boolean isCoveredBy(Marking other) {
        for (int p = 0; p < tokens.length; p++) {
            if (tokens[p] > other.tokens[p]) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return tokens.length;
    }

    public int[] toArray() {
        return tokens.clone();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Marking other && Arrays.equals(tokens, other.tokens));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(tokens[i] == OMEGA ? "ω" : Integer.toString(tokens[i]));
        }
        return sb.append(')').toString();
    }
}
