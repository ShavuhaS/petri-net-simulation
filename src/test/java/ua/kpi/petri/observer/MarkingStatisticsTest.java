package ua.kpi.petri.observer;

import org.junit.jupiter.api.Test;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.observer.MarkingStatistics.PlaceStatistics;
import ua.kpi.petri.simulation.NaiveEnabledTransitionsFinder;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.Simulator;

import java.nio.file.Path;
import java.util.List;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkingStatisticsTest {

    /**
     * shared-resource is deterministic: Take and Release alternate.
     * Markings (Queue Free Busy Done) for ticks 0..6:
     * (3 1 0 0) (2 0 1 0) (2 1 0 1) (1 0 1 1) (1 1 0 2) (0 0 1 2) (0 1 0 3), then deadlock.
     */
    @Test
    void sharedResourceStatisticsMatchHandCalculation() {
        PetriNet net = NetJson.read(Path.of("examples/shared-resource.json"));
        MarkingStatistics statistics = new MarkingStatistics();
        FiringCounter firings = new FiringCounter();
        SimulationResult result = new Simulator(net, new NaiveEnabledTransitionsFinder(net),
                RandomGeneratorFactory.of("L64X128MixRandom").create(1))
                .addListener(statistics)
                .addListener(firings)
                .run(100);

        assertEquals(6, result.ticks());
        assertEquals(7, statistics.getObservations());

        List<PlaceStatistics> stats = statistics.getStatistics();
        assertPlace(stats.get(0), "Queue", 9.0 / 7, 0, 3);
        assertPlace(stats.get(1), "Free", 4.0 / 7, 0, 1);
        assertPlace(stats.get(2), "Busy", 3.0 / 7, 0, 1);
        assertPlace(stats.get(3), "Done", 9.0 / 7, 0, 3);

        assertEquals(3L, firings.getCounts().get("Take"));
        assertEquals(3L, firings.getCounts().get("Release"));
    }

    private static void assertPlace(PlaceStatistics s, String name, double mean, int min, int max) {
        assertEquals(name, s.place());
        assertEquals(mean, s.mean(), 1e-12);
        assertEquals(min, s.min());
        assertEquals(max, s.max());
    }
}
