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

class SimulateCommandTest {

    private static String run(String... args) {
        StringWriter out = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));
        assertEquals(0, cli.execute(args), out::toString);
        return out.toString();
    }

    @Test
    void printsProtocolAndStatistics() {
        String out = run("simulate", "examples/shared-resource.json", "--seed", "1", "--protocol", "-");

        assertTrue(out.contains("tick  event                 marking (Queue Free Busy Done)"), out);
        assertTrue(out.contains("0     initial               (3 1 0 0)"), out);
        assertTrue(out.contains("1     Take                  (2 0 1 0)"), out);
        assertTrue(out.contains("6     stop: deadlock"), out);
        assertTrue(out.contains("Ticks:  6 (stopped by deadlock)"), out);
        assertTrue(out.contains("Queue                    1.2857      0      3"), out);
    }

    @Test
    void writesCsvAndProtocolFiles(@TempDir Path dir) throws IOException {
        Path protocol = dir.resolve("protocol.txt");
        String out = run("simulate", "examples/shared-resource.json", "--seed", "3", "--quiet",
                "--protocol", protocol.toString(), "--out", dir.resolve("results").toString());

        assertEquals("", out);
        List<String> places = Files.readAllLines(dir.resolve("results/place-statistics.csv"));
        assertEquals("place,mean,min,max", places.getFirst());
        assertEquals("Queue,1.285714,0,3", places.get(1));
        assertEquals(List.of("transition,firings", "Take,3", "Release,3"),
                Files.readAllLines(dir.resolve("results/transition-firings.csv")));
        assertTrue(Files.readString(protocol).contains("6     stop: deadlock"));
    }
}
