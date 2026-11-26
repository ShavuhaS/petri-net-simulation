package ua.kpi.petri.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import ua.kpi.petri.analysis.ExactReachabilityAnalyzer;
import ua.kpi.petri.analysis.ReachabilityComparison;
import ua.kpi.petri.analysis.ReachabilityResult;
import ua.kpi.petri.analysis.SimulatedReachability;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.Marking;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.simulation.SimulationSeries;

@Command(name = "reachability", mixinStandardHelpOptions = true,
        description = "Find the set of reachable markings of a net, by simulation or exactly (--exact).")
final class ReachabilityCommand implements Runnable {

    private static final int PRINT_LIMIT = 100;

    @Parameters(index = "0", paramLabel = "NET", description = "Net definition file (JSON).")
    private Path netFile;

    @Option(names = "--exact", description = "Build the reachability tree with ω instead of simulating.")
    private boolean exact;

    @Option(names = "--max-nodes", defaultValue = "" + ExactReachabilityAnalyzer.DEFAULT_MAX_NODES,
            description = "Node limit of the reachability tree (default: ${DEFAULT-VALUE}).")
    private int maxNodes;

    @Mixin
    private RunOptions runOptions;

    @Mixin
    private OutputOptions output;

    @Option(names = {"--runs", "-r"}, description = "Simulate exactly N runs instead of running until saturation.")
    private Integer runs;

    @Option(names = "--until-saturated", defaultValue = "100", paramLabel = "K",
            description = "Simulate until K runs in a row find no new marking (default: ${DEFAULT-VALUE}).")
    private int window;

    @Option(names = "--max-runs", defaultValue = "10000",
            description = "Upper limit of runs when simulating until saturation (default: ${DEFAULT-VALUE}).")
    private int maxRuns;

    @Option(names = "--compare-exact", description = "Also build the reachability tree and compare the sets.")
    private boolean compareExact;

    @Spec
    private CommandSpec spec;

    private SimulatedReachability.Result simulatedResult;
    private ReachabilityResult exactResult;

    @Override
    public void run() {
        PetriNet net = NetJson.read(netFile);
        if (exact) {
            exact(net);
        } else {
            simulated(net);
        }
        spec.commandLine().getOut().flush();
    }

    private void exact(PetriNet net) {
        ReachabilityResult result = new ExactReachabilityAnalyzer(net, maxNodes).analyze();
        if (!output.quiet) {
            printExact(spec.commandLine().getOut(), result);
        }
        if (output.outDir != null) {
            Map<Marking, Integer> ids = writeMarkings(net, result.markings(), Set.copyOf(result.terminal()));
            CsvWriter edges = new CsvWriter("from", "transition", "to");
            for (ReachabilityResult.Edge edge : result.edges()) {
                edges.row(ids.get(edge.from()), edge.transition(), ids.get(edge.to()));
            }
            edges.write(output.outDir.resolve("reachability-graph.csv"));

            CsvWriter bounds = new CsvWriter("place", "bound");
            for (Place place : net.getPlaces()) {
                bounds.row(place.getName(), tokens(result.bound(place)));
            }
            bounds.write(output.outDir.resolve("place-bounds.csv"));

            CsvWriter transitions = new CsvWriter("transition", "dead");
            for (var transition : net.getTransitions()) {
                transitions.row(transition.getName(), result.deadTransitions().contains(transition.getName()));
            }
            transitions.write(output.outDir.resolve("dead-transitions.csv"));
        }
    }

    private void simulated(PetriNet net) {
        SimulationSeries series = runOptions.series(net);
        SimulatedReachability analysis = new SimulatedReachability(series, runOptions.confidence());
        SimulatedReachability.Result result = runs != null
                ? analysis.runFixed(runs)
                : analysis.runUntilSaturated(window, maxRuns);
        exactResult = compareExact ? new ExactReachabilityAnalyzer(net, maxNodes).analyze() : null;
        simulatedResult = result;

        if (!output.quiet) {
            printSimulated(spec.commandLine().getOut(), series, result, exactResult);
        }
        if (output.outDir != null) {
            writeMarkings(net, result.markings(), Set.copyOf(result.terminal()));

            CsvWriter saturation = new CsvWriter("run", "new_markings", "total_markings");
            int total = 0;
            for (int run = 0; run < result.runCount(); run++) {
                total += result.newPerRun().get(run);
                saturation.row(run, result.newPerRun().get(run), total);
            }
            saturation.write(output.outDir.resolve("saturation.csv"));
        }
    }

    /** Result of the last simulated run of this command, for experiments that summarise many nets. */
    SimulatedReachability.Result simulatedResult() {
        return simulatedResult;
    }

    /** Reachability tree built by the last run with {@code --compare-exact}, or null. */
    ReachabilityResult exactResult() {
        return exactResult;
    }

    private static void printExact(PrintWriter out, ReachabilityResult result) {
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
        out.println("Markings " + placeHeader(net) + ":");
        printMarkings(out, result.markings());
        out.println();
        out.println("Terminal markings (deadlocks):");
        printMarkings(out, result.terminal());
        out.println();
        out.println("Dead transitions (never enabled): "
                + (result.deadTransitions().isEmpty() ? "none" : String.join(", ", result.deadTransitions())));
    }

    private void printSimulated(PrintWriter out, SimulationSeries series, SimulatedReachability.Result result,
                                ReachabilityResult exact) {
        PetriNet net = series.net();
        int n = result.runCount();
        out.println(net);
        out.printf("Runs:   %d (seeds %d .. %d, tick limit %d); %d ended in a deadlock%n",
                n, series.seedOf(0), series.seedOf(n - 1), series.maxTicks(), result.deadlockRuns());
        out.printf("Distinct markings met: %d%n", result.markings().size());
        out.printf("Last %d runs in a row found no new marking", result.runsWithoutNew());
        if (runs == null) {
            out.print(result.runsWithoutNew() >= window ? " (saturated)" : " (NOT saturated: run limit reached)");
        }
        out.println();
        if (result.runsWithoutNew() > 0) {
            out.printf(Locale.ROOT, "Probability that one more run finds a new marking: below %.4f (confidence %s)%n",
                    result.newMarkingBound(), result.confidence());
        }
        out.println();

        out.println("Markings " + placeHeader(net) + ":");
        printMarkings(out, result.markings());
        out.println();
        out.println("Dead markings met:");
        printMarkings(out, result.terminal());

        if (exact != null) {
            out.println();
            printComparison(out, result, exact);
        }
    }

    private static void printComparison(PrintWriter out, SimulatedReachability.Result simulated,
                                        ReachabilityResult exact) {
        if (!exact.complete()) {
            out.println("Comparison with the reachability tree: not possible (node limit reached)");
            return;
        }
        ReachabilityComparison c = ReachabilityComparison.of(simulated, exact);
        out.printf("Comparison with the reachability tree (%s):%n",
                exact.isBounded() ? "bounded net" : "unbounded net");
        out.printf("  markings met that are markings of the tree: %d of %d%n", c.inTree(), c.treeMarkings());
        out.printf("  markings met that no tree marking covers:   %d%n", c.notCovered().size());
        c.notCovered().stream().limit(PRINT_LIMIT).forEach(m -> out.println("    " + m));
        out.printf("  dead markings met: %d of %d%n", c.deadMet(), c.deadInTree());
    }

    private static String placeHeader(PetriNet net) {
        return net.getPlaces().stream().map(Place::getName).collect(Collectors.joining(" ", "(", ")"));
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

    private Map<Marking, Integer> writeMarkings(PetriNet net, List<Marking> markings, Set<Marking> terminal) {
        Map<Marking, Integer> ids = new HashMap<>();
        String[] header = new String[net.getPlaces().size() + 2];
        header[0] = "id";
        for (Place place : net.getPlaces()) {
            header[place.getIndex() + 1] = place.getName();
        }
        header[header.length - 1] = "terminal";
        CsvWriter csv = new CsvWriter(header);
        for (Marking marking : markings) {
            int id = ids.size();
            ids.put(marking, id);
            Object[] row = new Object[header.length];
            row[0] = id;
            for (int p = 0; p < marking.size(); p++) {
                row[p + 1] = tokens(marking.get(p));
            }
            row[row.length - 1] = terminal.contains(marking);
            csv.row(row);
        }
        csv.write(output.outDir.resolve("reachable-markings.csv"));
        return ids;
    }

    /** ω is written as "inf" so that pandas reads the column as numbers. */
    private static String tokens(int count) {
        return count == Marking.OMEGA ? "inf" : Integer.toString(count);
    }
}
