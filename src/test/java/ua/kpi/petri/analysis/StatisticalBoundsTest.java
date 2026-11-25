package ua.kpi.petri.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticalBoundsTest {

    /** Chebyshev column of the table comparing run counts (sigma/epsilon = 1, 2, 4, 6, 8, 10; confidence 0.95). */
    @Test
    void chebyshevRunsMatchLectureTable() {
        int[] expected = {20, 80, 320, 720, 1280, 2000};
        int[] ratio = {1, 2, 4, 6, 8, 10};
        for (int i = 0; i < ratio.length; i++) {
            assertEquals(expected[i], StatisticalBounds.chebyshevRuns(ratio[i] * ratio[i], 1.0, 0.95));
        }
    }

    @Test
    void proportionNeeds2000RunsForFivePercentAccuracy() {
        assertEquals(2000, StatisticalBounds.chebyshevRuns(StatisticalBounds.PROPORTION_MAX_VARIANCE, 0.05, 0.95));
        assertEquals(0.05, StatisticalBounds.chebyshevError(StatisticalBounds.PROPORTION_MAX_VARIANCE, 2000, 0.95),
                1e-12);
    }

    @Test
    void zeroOccurrenceBoundSolvesTheEquation() {
        for (int runs : new int[]{1, 10, 100, 2000}) {
            double q = StatisticalBounds.zeroOccurrenceBound(runs, 0.95);
            assertEquals(0.05, Math.pow(1 - q, runs), 1e-12);
            assertTrue(q < 3.0 / runs);
        }
        assertEquals(0.0295, StatisticalBounds.zeroOccurrenceBound(100, 0.95), 1e-4);
    }

    @Test
    void runningStatisticsGivesExactZeroVarianceForEqualValues() {
        RunningStatistics equal = new RunningStatistics();
        for (int i = 0; i < 5; i++) {
            equal.add(9.0 / 7);
        }
        assertEquals(0.0, equal.variance());
        assertEquals(9.0 / 7, equal.mean(), 1e-15);

        RunningStatistics values = new RunningStatistics();
        for (double v : new double[]{1, 2, 3, 4}) {
            values.add(v);
        }
        assertEquals(5.0 / 3, values.variance(), 1e-12);

        RunningStatistics single = new RunningStatistics();
        single.add(1);
        assertTrue(Double.isNaN(single.variance()));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> StatisticalBounds.chebyshevRuns(1, 0, 0.95));
        assertThrows(IllegalArgumentException.class, () -> StatisticalBounds.chebyshevRuns(1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> StatisticalBounds.zeroOccurrenceBound(0, 0.95));
    }
}
