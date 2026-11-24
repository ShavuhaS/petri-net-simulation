package ua.kpi.petri.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoCommandTest {

    @Test
    void printsStructureOfExampleNet() {
        StringWriter out = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));

        int exitCode = cli.execute("info", "examples/lecture7-abc.json");

        assertEquals(0, exitCode);
        assertTrue(out.toString().contains("C                    [P2*2] -> [P4]"), out::toString);
        assertTrue(out.toString().contains("Initial marking: (1 1 0 0)"), out::toString);
    }

    @Test
    void reportsInvalidNetWithNonZeroExitCode(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, """
                { "name": "n", "places": [], "transitions": [ { "name": "T", "in": { "X": 1 } } ] }
                """);
        StringWriter err = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setErr(new PrintWriter(err));

        int exitCode = cli.execute("info", file.toString());

        assertEquals(1, exitCode);
        assertTrue(err.toString().contains("unknown place 'X'"), err::toString);
    }
}
