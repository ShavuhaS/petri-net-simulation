package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.model.Transition;

import java.io.PrintWriter;
import java.nio.file.Path;

@Command(name = "info", mixinStandardHelpOptions = true, description = "Load a net from JSON, validate it and print its structure.")
final class InfoCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "NET", description = "Net definition file (JSON).")
    private Path netFile;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        PetriNet net = NetJson.read(netFile);
        PrintWriter out = spec.commandLine().getOut();

        out.println(net);
        out.println();
        out.println("Places:");
        for (Place place : net.getPlaces()) {
            out.printf("  %-20s %d%n", place.getName(), place.getInitialTokens());
        }
        out.println();
        out.println("Transitions:");
        for (Transition transition : net.getTransitions()) {
            out.printf("  %-20s %s -> %s%n", transition.getName(), transition.getInputs(), transition.getOutputs());
        }
        out.println();
        out.println("Initial marking: " + net.initialMarking());
        out.flush();
    }
}
