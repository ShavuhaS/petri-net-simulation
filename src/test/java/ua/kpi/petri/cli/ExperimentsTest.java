package ua.kpi.petri.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentsTest {

    private final StringWriter console = new StringWriter();

    private Experiments experiments(Path out) {
        return new Experiments(out, new PrintWriter(console));
    }

    private static Experiments.NetSpec net(String name) {
        return Experiments.verificationNets().stream().filter(n -> n.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void verificationKeepsEveryStepOfEveryNet(@TempDir Path out) throws IOException {
        experiments(out).verification(List.of(net("conflict-chain"), net("philosophers-3")));

        Path root = out.resolve(Experiments.VERIFICATION);
        for (String name : List.of("conflict-chain", "philosophers-3")) {
            assertTrue(Files.exists(root.resolve("nets").resolve(name + ".json")), name);
            for (String step : List.of("1-structure", "2-single-run", "2-protocol", "3-series", "4-tree",
                    "5-simulated-reachability", "6-activity")) {
                assertTrue(Files.exists(root.resolve(name).resolve(step + ".txt")), name + " " + step);
            }
            assertTrue(Files.exists(root.resolve(name).resolve("tree").resolve("reachable-markings.csv")), name);
        }
        String tree = Files.readString(root.resolve("conflict-chain").resolve("4-tree.txt"));
        assertTrue(tree.startsWith("$ petri reachability "), tree);
        assertTrue(tree.contains("Reachability tree: 6 nodes, 5 distinct markings"), tree);
        assertTrue(console.toString().contains("=== Verification: philosophers-3"), console::toString);
    }

    @Test
    void reachabilitySummaryComparesEveryNetWithItsTree(@TempDir Path out) throws IOException {
        experiments(out).reachability(Experiments.reachabilityNets().subList(0, 2));

        Path root = out.resolve(Experiments.REACHABILITY);
        List<String> csv = Files.readAllLines(root.resolve("summary.csv"));
        assertEquals("net,places,transitions,tree_markings,markings_met,met_in_tree,runs,saturated,seconds", csv.get(0));
        assertTrue(csv.get(1).startsWith("philosophers-3,12,9,14,14,14,"), csv.get(1));
        assertTrue(csv.get(2).startsWith("philosophers-3-both-at-once,9,6,4,4,4,"), csv.get(2));
        assertTrue(csv.get(1).contains(",true,"), csv.get(1));
        assertTrue(Files.exists(root.resolve("philosophers-3").resolve("saturation.csv")));
        assertTrue(Files.readString(root.resolve("summary.txt")).contains("philosophers-3-both-at-once"));
    }

    @Test
    void activityOfDeadTransition(@TempDir Path out) throws IOException {
        experiments(out).activity(List.of(net("dead-transition")));

        String text = Files.readString(out.resolve(Experiments.ACTIVITY).resolve("dead-transition").resolve("activity.txt"));
        assertTrue(text.matches("(?s).*Never +0/2000 .*"), text);
    }

    @Test
    void benchmarkKeepsConsoleOutputAndMeasurements(@TempDir Path out) throws IOException {
        experiments(out).benchmark(List.of(new Experiments.BenchmarkRun("tiny", "series-x",
                List.of("--family", "queue-chain", "-n", "2", "-t", "1000", "--warmup", "0", "-r", "2"))));

        Path dir = out.resolve(Experiments.BENCHMARK).resolve("series-x");
        String text = Files.readString(dir.resolve("console.txt"));
        assertTrue(text.startsWith("$ petri benchmark --family queue-chain"), text);
        assertEquals(1 + 2 * 2, Files.readAllLines(dir.resolve("measurements.csv")).size());
    }

    @Test
    void benchmarkPlanHasThreeSeriesWithWarmUp() {
        List<Experiments.BenchmarkRun> runs = Experiments.benchmarkRuns(3);

        assertEquals(List.of("series-a", "series-b", "series-c"), runs.stream().map(Experiments.BenchmarkRun::dir).toList());
        assertTrue(runs.stream().allMatch(r -> r.args().containsAll(List.of("--warmup", "3", "-r", "20"))));
    }

    @Test
    void refusesToWriteOverEarlierResults(@TempDir Path out) throws IOException {
        Files.createDirectories(out.resolve(Experiments.VERIFICATION));

        StringWriter err = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(console));
        cli.setErr(new PrintWriter(err));
        int exitCode = cli.execute("experiments", "verification", "-o", out.toString());

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("already exists"), err::toString);
        assertThrows(IllegalStateException.class, () -> experiments(out).requireFresh(Experiments.VERIFICATION));
    }

    @Test
    void environmentNamesJavaAndProcessors(@TempDir Path out) throws IOException {
        experiments(out).environment();

        String text = Files.readString(out.resolve("environment.txt"));
        assertTrue(text.contains("Logical CPUs:"), text);
        assertTrue(text.contains("Java:"), text);
    }
}
