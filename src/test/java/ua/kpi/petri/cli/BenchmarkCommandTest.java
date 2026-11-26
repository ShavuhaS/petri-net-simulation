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
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCommandTest {

    private record Run(int exitCode, String out, String err) {
    }

    private static Run execute(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        int exitCode = cli.execute(args);
        return new Run(exitCode, out.toString(), err.toString());
    }

    @Test
    void stateMachineSeriesWritesEveryMeasurementAndSummary(@TempDir Path dir) throws IOException {
        Run run = execute("benchmark", "--family", "state-machine", "--places", "5", "--transitions", "5,10",
                "-t", "1000", "--warmup", "1", "-r", "3", "-o", dir.toString());

        assertEquals(0, run.exitCode(), run.err());
        assertTrue(run.out().contains("1000 ticks per run, 1 warm-up runs, 3 measured runs, seeds 1 .. 3"), run.out());
        List<String> measurements = Files.readAllLines(dir.resolve("measurements.csv"));
        assertEquals("family,places,transitions,finder,run,seed,ticks,nanos,ns_per_tick", measurements.getFirst());
        assertEquals(1 + 2 * 2 * 3, measurements.size());
        assertTrue(measurements.get(1).startsWith("state-machine,5,5,naive,0,1,1000,"), measurements.get(1));
        List<String> summary = Files.readAllLines(dir.resolve("summary.csv"));
        assertEquals(1 + 2 * 2, summary.size());
        assertTrue(summary.get(4).startsWith("state-machine,5,10,incremental,3,"), summary.get(4));
    }

    @Test
    void queueChainWithOneFinder() {
        Run run = execute("benchmark", "--family", "queue-chain", "-n", "2,4", "--finders", "incremental",
                "-t", "500", "--warmup", "0", "-r", "2");

        assertEquals(0, run.exitCode(), run.err());
        assertTrue(run.out().contains("queue-chain        8       5  incremental"), run.out());
        assertTrue(run.out().contains("queue-chain       14       9  incremental"), run.out());
    }

    @Test
    void rejectsMissingSizesAndUnknownFamily() {
        Run missing = execute("benchmark", "--family", "queue-chain");
        assertEquals(1, missing.exitCode());
        assertTrue(missing.err().contains("queue-chain needs --nodes"), missing.err());

        Run unknown = execute("benchmark", "--family", "random", "-n", "2");
        assertEquals(1, unknown.exitCode());
        assertTrue(unknown.err().contains("Unknown family 'random'"), unknown.err());
    }
}
