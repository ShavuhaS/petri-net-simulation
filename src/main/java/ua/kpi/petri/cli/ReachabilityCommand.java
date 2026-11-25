package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import ua.kpi.petri.analysis.ExactReachabilityAnalyzer;
import ua.kpi.petri.analysis.ReachabilityResult;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "reachability", mixinStandardHelpOptions = true,
        description = "Find the set of reachable markings of a net.")
final class ReachabilityCommand implements Runnable {

    private static final int PRINT_LIMIT = 100;

    @Parameters(index = "0", paramLabel = "NET", description = "Net definition file (JSON).")
    private Path netFile;

    @Option(names = "--exact", description = "Build the reachability tree with ω (exhaustive, no simulation).")
    private boolean exact;

    @Option(names = "--max-nodes", defaultValue = "" + ExactReachabilityAnalyzer.DEFAULT_MAX_NODES,
            description = "Node limit of the reachability tree (default: ${DEFAULT-VALUE}).")
    private int maxNodes;

    @Option(names = "--out", paramLabel = "DIR", description = "Write CSV results to DIR.")
    private Path outDir;

    @Option(names = "--quiet", description = "Do not print result tables.")
    private boolean quiet;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        if (!exact) {
            throw new IllegalArgumentException("Only --exact is implemented so far");
        }
        PetriNet net = NetJson.read(netFile);
        ReachabilityResult result = new ExactReachabilityAnalyzer(net, maxNodes).analyze();

        if (!quiet) {
            print(spec.commandLine().getOut(), result);
        }
        if (outDir != null) {
            writeCsv(result);
        }
        spec.commandLine().getOut().flush();
    }

    private static void print(PrintWriter out, ReachabilityResult result) {
        PetriNet net = result.net();
        out.println(net);
        out.printf("Reachability tree: %d nodes, %d distinct markings%s%n", result.nodes(), result.markings().size(),
                result.complete() ? "" : " (INCOMPLETE: node limit reached)");
        if (result.isBounded()) {
            out.printf("Bounded: yes, k = %d%n", result.boundK());
        } else {
            out.println("Bounded: no, unbounded places: " + String.join(", ", result.unboundedPlaces()));
        }
        out.println();

        String header = net.getPlaces().stream().map(Place::getName).collect(Collectors.joining(" ", "(", ")"));
        out.println("Markings " + header + ":");
        printMarkings(out, result.markings());
        out.println();
        out.println("Terminal markings (deadlocks):");
        printMarkings(out, result.terminal());
        out.println();
        out.println("Dead transitions (never enabled): "
                + (result.deadTransitions().isEmpty() ? "none" : String.join(", ", result.deadTransitions())));
    }

    private static void printMarkings(PrintWriter out, List<Marking> markings) {
        if (markings.isEmpty()) {
            out.println("  none");
            return;
        }
        markings.stream().limit(PRINT_LIMIT).forEach(m -> out.println("  " + m));
        if (markings.size() > PRINT_LIMIT) {
            out.printf("  ... %d more (use --out to get all)%n", markings.size() - PRINT_LIMIT);
        }
    }

    private void writeCsv(ReachabilityResult result) {
        PetriNet net = result.net();
        Map<Marking, Integer> ids = new HashMap<>();

        String[] header = new String[net.getPlaces().size() + 2];
        header[0] = "id";
        for (Place place : net.getPlaces()) {
            header[place.getIndex() + 1] = place.getName();
        }
        header[header.length - 1] = "terminal";
        CsvWriter markings = new CsvWriter(header);
        for (Marking marking : result.markings()) {
            int id = ids.size();
            ids.put(marking, id);
            Object[] row = new Object[header.length];
            row[0] = id;
            for (int p = 0; p < marking.size(); p++) {
                row[p + 1] = tokens(marking.get(p));
            }
            row[row.length - 1] = result.terminal().contains(marking);
            markings.row(row);
        }
        markings.write(outDir.resolve("reachable-markings.csv"));

        CsvWriter edges = new CsvWriter("from", "transition", "to");
        for (ReachabilityResult.Edge edge : result.edges()) {
            edges.row(ids.get(edge.from()), edge.transition(), ids.get(edge.to()));
        }
        edges.write(outDir.resolve("reachability-graph.csv"));

        CsvWriter bounds = new CsvWriter("place", "bound");
        for (Place place : net.getPlaces()) {
            bounds.row(place.getName(), tokens(result.bound(place)));
        }
        bounds.write(outDir.resolve("place-bounds.csv"));

        CsvWriter transitions = new CsvWriter("transition", "dead");
        for (var transition : net.getTransitions()) {
            transitions.row(transition.getName(), result.deadTransitions().contains(transition.getName()));
        }
        transitions.write(outDir.resolve("dead-transitions.csv"));
    }

    /** ω is written as "inf" so that pandas reads the column as numbers. */
    private static String tokens(int count) {
        return count == Marking.OMEGA ? "inf" : Integer.toString(count);
    }
}
