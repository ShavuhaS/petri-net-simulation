package ua.kpi.petri.analysis;

import ua.kpi.petri.model.Marking;

import static ua.kpi.petri.model.Marking.OMEGA;

/**
 * Introduction of ω on a path of markings: a place gets ω when an earlier marking on the path is
 * covered by the new marking and holds fewer tokens in that place. Once set, ω stays.
 */
final class OmegaRule {

    private OmegaRule() {
    }

    /**
     * The new marking with ω added for every earlier marking of the path that it covers.
     * Repeats until nothing changes, because a new ω can make another earlier marking covered.
     */
    static Marking apply(Marking marking, Iterable<Marking> path) {
        int[] tokens = marking.toArray();
        boolean omegaAdded = false;
        boolean changed = true;
        while (changed) {
            changed = false;
            Marking current = new Marking(tokens);
            for (Marking previous : path) {
                if (previous.isCoveredBy(current)) {
                    for (int p = 0; p < tokens.length; p++) {
                        if (previous.get(p) < tokens[p] && tokens[p] != OMEGA) {
                            tokens[p] = OMEGA;
                            changed = true;
                            omegaAdded = true;
                        }
                    }
                }
            }
        }
        return omegaAdded ? new Marking(tokens) : marking;
    }
}
