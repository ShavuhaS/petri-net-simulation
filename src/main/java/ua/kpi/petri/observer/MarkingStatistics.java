package ua.kpi.petri.observer;

import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;

import java.util.ArrayList;
import java.util.List;

/**
 * Mean, minimum and maximum marking of every place over the observed ticks 0..T,
 * where each tick is one unit of model time (dt = 1).
 */
public final class MarkingStatistics implements SimulationListener {

    public record PlaceStatistics(String place, double mean, int min, int max) {
    }

    private List<Place> places = List.of();
    private long[] sums;
    private int[] mins;
    private int[] maxs;
    private long observations;

    @Override
    public void onStart(PetriNet net) {
        places = net.getPlaces();
        int n = places.size();
        sums = new long[n];
        mins = new int[n];
        maxs = new int[n];
        for (int i = 0; i < n; i++) {
            int tokens = places.get(i).getTokens();
            mins[i] = tokens;
            maxs[i] = tokens;
        }
        observations = 0;
        observe();
    }

    @Override
    public void onTickEnd(int tick) {
        observe();
    }

    private void observe() {
        for (int i = 0; i < sums.length; i++) {
            int tokens = places.get(i).getTokens();
            sums[i] += tokens;
            if (tokens < mins[i]) {
                mins[i] = tokens;
            }
            if (tokens > maxs[i]) {
                maxs[i] = tokens;
            }
        }
        observations++;
    }

    public long getObservations() {
        return observations;
    }

    public List<PlaceStatistics> getStatistics() {
        List<PlaceStatistics> result = new ArrayList<>(places.size());
        for (int i = 0; i < places.size(); i++) {
            result.add(new PlaceStatistics(places.get(i).getName(),
                    (double) sums[i] / observations, mins[i], maxs[i]));
        }
        return result;
    }
}
