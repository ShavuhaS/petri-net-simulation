package ua.kpi.petri.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateCommandTest {

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
    void writesGeneratedNetToFile(@TempDir Path dir) {
        Path file = dir.resolve("chain.json");
        Run run = execute("generate", "queue-chain", "-n", "4", "-o", file.toString());

        assertEquals(0, run.exitCode(), run.err());
        assertTrue(run.out().contains("written to " + file), run.out());
        PetriNet net = NetJson.read(file);
        assertEquals("queue-chain-n4-c1", net.getName());
        assertEquals(14, net.getPlaces().size());
        assertEquals(9, net.getTransitions().size());
    }

    @Test
    void printsJsonWithoutOutputFile() {
        Run run = execute("generate", "state-machine", "--places", "3", "--transitions", "5", "-s", "2");

        assertEquals(0, run.exitCode(), run.err());
        PetriNet net = NetJson.parse(run.out());
        assertEquals("state-machine-P3-T5-seed2", net.getName());
        assertEquals(5, net.getTransitions().size());
    }

    @Test
    void philosophersOptionAndInvalidSize() {
        Run run = execute("generate", "philosophers", "-n", "3", "--both-at-once");
        assertEquals(0, run.exitCode(), run.err());
        assertEquals(6, NetJson.parse(run.out()).getTransitions().size());

        Run invalid = execute("generate", "state-machine", "--places", "5", "--transitions", "4");
        assertEquals(1, invalid.exitCode());
        assertTrue(invalid.err().contains("at least as many transitions as places"), invalid.err());
    }
}
