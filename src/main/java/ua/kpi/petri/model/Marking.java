package ua.kpi.petri.model;

import java.util.Arrays;

/**
 * Immutable snapshot of token counts, ordered as the places of the net.
 * Safe to use as a HashSet/HashMap key.
 */
public final class Marking {

    private final int[] tokens;
    private final int hash;

    public Marking(int... tokens) {
        this.tokens = tokens.clone();
        this.hash = Arrays.hashCode(this.tokens);
    }

    public int get(int placeIndex) {
        return tokens[placeIndex];
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
            sb.append(tokens[i]);
        }
        return sb.append(')').toString();
    }
}
