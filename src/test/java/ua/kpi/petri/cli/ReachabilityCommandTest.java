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

class ReachabilityCommandTest {

    @Test
    void printsExactReachabilitySet() {
        StringWriter out = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));

        assertEquals(0, cli.execute("reachability", "examples/lecture7-abc.json", "--exact"));
        String text = out.toString();
        assertTrue(text.contains("6 distinct markings"), text);
        assertTrue(text.contains("Bounded: yes, k = 2"), text);
        assertTrue(text.contains("Markings (P1 P2 P3 P4):"), text);
        assertTrue(text.contains("  (0 0 0 1)"), text);
        assertTrue(text.contains("Dead transitions (never enabled): none"), text);
    }

    @Test
    void writesCsvWithOmegaAsInf(@TempDir Path dir) throws IOException {
        Path net = dir.resolve("gen.json");
        Files.writeString(net, """
                { "name": "gen", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" } ],
                  "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } } ] }
                """);
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(new StringWriter()));

        assertEquals(0, cli.execute("reachability", net.toString(), "--exact", "--quiet", "--out", dir.toString()));
        assertEquals(List.of("id,Gen,Queue,terminal", "0,1,0,false", "1,1,inf,false"),
                Files.readAllLines(dir.resolve("reachable-markings.csv")));
        assertEquals(List.of("from,transition,to", "0,Arrival,1", "1,Arrival,1"),
                Files.readAllLines(dir.resolve("reachability-graph.csv")));
        assertEquals(List.of("place,bound", "Gen,1", "Queue,inf"),
                Files.readAllLines(dir.resolve("place-bounds.csv")));
    }

    @Test
    void simulatedModeSaturatesAndMatchesTheTree() {
        StringWriter out = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));

        assertEquals(0, cli.execute("reachability", "examples/lecture7-abc.json", "-s", "1",
                "--until-saturated", "30", "--compare-exact"));
        String text = out.toString();
        assertTrue(text.contains("Distinct markings met: 6"), text);
        assertTrue(text.contains("(saturated)"), text);
        assertTrue(text.contains("Comparison with the reachability tree (bounded net):"), text);
        assertTrue(text.contains("markings met that are markings of the tree: 6 of 6"), text);
        assertTrue(text.contains("markings met that no tree marking covers:   0"), text);
        assertTrue(text.contains("dead markings met: 2 of 2"), text);
    }

    @Test
    void simulatedModeUsesOmegaForUnboundedNet(@TempDir Path dir) throws IOException {
        Path net = dir.resolve("gen.json");
        Files.writeString(net, """
                { "name": "gen", "places": [ { "name": "Gen", "tokens": 1 }, { "name": "Queue" } ],
                  "transitions": [ { "name": "Arrival", "in": { "Gen": 1 }, "out": { "Gen": 1, "Queue": 1 } } ] }
                """);
        StringWriter out = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));

        assertEquals(0, cli.execute("reachability", net.toString(), "-s", "1", "--until-saturated", "10",
                "--compare-exact"));
        String text = out.toString();
        assertTrue(text.contains("Distinct markings met: 2"), text);
        assertTrue(text.contains("  (1 ω)"), text);
        assertTrue(text.contains("(unbounded net)"), text);
        assertTrue(text.contains("markings met that are markings of the tree: 2 of 2"), text);
        assertTrue(text.contains("markings met that no tree marking covers:   0"), text);
    }
}
