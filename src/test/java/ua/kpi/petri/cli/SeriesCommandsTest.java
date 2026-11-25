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

class SeriesCommandsTest {

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
    void simulateSeriesPrintsMeansAndWritesRunsCsv(@TempDir Path dir) throws IOException {
        Run run = execute("simulate", "examples/shared-resource.json", "-r", "5", "-s", "10", "-o", dir.toString());

        assertEquals(0, run.exitCode(), run.err());
        assertTrue(run.out().contains("Runs:   5"), run.out());
        assertTrue(run.out().contains("Seeds:  10 .. 14"), run.out());
        assertTrue(run.out().contains("5 of 5 runs ended in a deadlock"), run.out());
        assertTrue(run.out().contains("Queue                    1.2857     0.0000     0.0000      0      3"), run.out());
        List<String> runs = Files.readAllLines(dir.resolve("runs.csv"));
        assertEquals("run,seed,ticks,deadlock,mean_Queue,mean_Free,mean_Busy,mean_Done", runs.getFirst());
        assertEquals(6, runs.size());
    }

    @Test
    void simulateAutoReportsPilotAndChebyshev() {
        Run run = execute("simulate", "examples/lecture7-abc.json", "-r", "auto", "-s", "1");

        assertEquals(0, run.exitCode(), run.err());
        assertTrue(run.out().contains("(pilot series 20; Chebyshev's inequality requires"), run.out());
    }

    @Test
    void protocolNeedsSingleRunAndRunsMustBeValid() {
        Run protocol = execute("simulate", "examples/lecture7-abc.json", "-r", "3", "-p", "-");
        assertEquals(1, protocol.exitCode());
        assertTrue(protocol.err().contains("--protocol is written for a single run only"), protocol.err());

        Run invalid = execute("simulate", "examples/lecture7-abc.json", "-r", "many");
        assertEquals(2, invalid.exitCode());
        assertTrue(invalid.err().contains("expected a positive number or 'auto'"), invalid.err());
    }

    @Test
    void activityReportsLevelsAndDeadBound(@TempDir Path dir) throws IOException {
        Path net = dir.resolve("dead.json");
        Files.writeString(net, """
                { "name": "dead", "places": [ { "name": "P", "tokens": 1 }, { "name": "Q" } ],
                  "transitions": [ { "name": "Move", "in": { "P": 1 }, "out": { "Q": 1 } },
                                   { "name": "Never", "in": { "P": 2 }, "out": { "Q": 1 } } ] }
                """);
        Run run = execute("activity", net.toString(), "-r", "100", "-s", "1", "-o", dir.toString());

        assertEquals(0, run.exitCode(), run.err());
        assertTrue(run.out().contains("Never                       0/100"), run.out());
        assertTrue(run.out().contains("its probability to fire in a run is below 0.0295"), run.out());
        assertTrue(run.out().contains("Level 4: excluded for every transition, 100 of 100 runs"), run.out());
        List<String> csv = Files.readAllLines(dir.resolve("activity.csv"));
        assertTrue(csv.get(1).startsWith("Move,100,100,1.000000"), csv.get(1));
        assertTrue(csv.get(2).startsWith("Never,0,100,0.000000"), csv.get(2));
        assertTrue(csv.get(2).contains(",0,0.029513"), csv.get(2));
    }
}
