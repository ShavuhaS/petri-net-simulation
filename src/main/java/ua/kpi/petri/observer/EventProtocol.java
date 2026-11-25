package ua.kpi.petri.observer;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.model.Transition;
import ua.kpi.petri.simulation.SimulationResult;

import java.io.PrintWriter;

/**
 * Writes one line per event: tick, fired transition and the marking right after firing.
 */
public final class EventProtocol implements SimulationListener {

    private final PrintWriter out;
    private PetriNet net;

    public EventProtocol(PrintWriter out) {
        this.out = out;
    }

    @Override
    public void onStart(PetriNet net) {
        this.net = net;
        StringBuilder header = new StringBuilder("tick  event                 marking (");
        for (int i = 0; i < net.getPlaces().size(); i++) {
            if (i > 0) {
                header.append(' ');
            }
            header.append(net.getPlaces().get(i).getName());
        }
        out.println(header.append(')'));
        line(0, "initial");
    }

    @Override
    public void onFire(int tick, Transition transition) {
        line(tick, transition.getName());
    }

    @Override
    public void onEnd(SimulationResult result) {
        String reason = switch (result.stopReason()) {
            case DEADLOCK -> "stop: deadlock (no enabled transitions)";
            case MAX_TICKS -> "stop: tick limit reached";
        };
        out.printf("%-5d %s%n", result.ticks(), reason);
        out.flush();
    }

    private void line(int tick, String event) {
        StringBuilder marking = new StringBuilder("(");
        for (int i = 0; i < net.getPlaces().size(); i++) {
            Place place = net.getPlaces().get(i);
            if (i > 0) {
                marking.append(' ');
            }
            marking.append(place.getTokens());
        }
        out.printf("%-5d %-21s %s)%n", tick, event, marking);
    }
}
