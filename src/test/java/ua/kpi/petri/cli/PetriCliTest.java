package ua.kpi.petri.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetriCliTest {

    @Test
    void withoutSubcommandPrintsUsageToCommandOutput() {
        StringWriter out = new StringWriter();
        CommandLine cli = PetriCli.newCommandLine();
        cli.setOut(new PrintWriter(out));

        assertEquals(0, cli.execute());
        assertTrue(out.toString().contains("Usage: petri"), out::toString);
        assertTrue(out.toString().contains("simulate"), out::toString);
    }
}
