package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import ua.kpi.petri.benchmark.Benchmark;
import ua.kpi.petri.benchmark.Benchmark.Measurement;
import ua.kpi.petri.benchmark.Benchmark.Point;
import ua.kpi.petri.generator.NetGenerators;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.simulation.FinderType;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Command(name = "benchmark", mixinStandardHelpOptions = true,
        description = {"Measure the time of one simulation step on generated nets of growing size.",
                "Run the JVM with a fixed heap, e.g. java -Xms4g -Xmx4g -jar petri-net-simulation.jar benchmark ..."})
final class BenchmarkCommand implements Runnable {

    @Option(names = "--family", required = true, paramLabel = "state-machine|queue-chain",
            description = "Family of generated nets.")
    private String family;

    @Option(names = "--places", split = ",", description = "Numbers of places (state-machine).")
    private List<Integer> places = List.of();

    @Option(names = "--transitions", split = ",", description = "Numbers of transitions (state-machine).")
    private List<Integer> transitions = List.of();

    @Option(names = {"--nodes", "-n"}, split = ",", description = "Numbers of queueing nodes (queue-chain).")
    private List<Integer> nodes = List.of();

    @Option(names = "--servers", defaultValue = "1", description = "Servers in each node (default: ${DEFAULT-VALUE}).")
    private int servers;

    @Option(names = "--net-seed", defaultValue = "1",
            description = "Seed of the random transitions of a state machine (default: ${DEFAULT-VALUE}).")
    private long netSeed;

    @Option(names = "--finders", split = ",", defaultValue = "naive,incremental",
            description = "Ways of finding enabled transitions: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private List<FinderType> finders;

    @Option(names = {"--ticks", "-t"}, defaultValue = "100000", description = "Ticks of one run (default: ${DEFAULT-VALUE}).")
    private int ticks;

    @Option(names = "--warmup", defaultValue = "5", description = "Runs before measuring, not recorded (default: ${DEFAULT-VALUE}).")
    private int warmup;

    @Option(names = {"--runs", "-r"}, defaultValue = "20", description = "Measured runs (default: ${DEFAULT-VALUE}).")
    private int runs;

    @Option(names = {"--seed", "-s"}, defaultValue = "1", description = "Seed of the first run; run i uses seed + i (default: ${DEFAULT-VALUE}).")
    private long seed;

    @Mixin
    private OutputOptions output;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        List<PetriNet> nets = nets();
        Benchmark benchmark = new Benchmark(ticks, warmup, runs, seed);
        PrintWriter out = spec.commandLine().getOut();
        if (!output.quiet) {
            Runtime runtime = Runtime.getRuntime();
            out.printf("Java %s (%s), %s %s, %d processors, max heap %d MiB%n", Runtime.version(),
                    System.getProperty("java.vm.name"), System.getProperty("os.name"), System.getProperty("os.arch"),
                    runtime.availableProcessors(), runtime.maxMemory() / (1024 * 1024));
            out.printf("%d ticks per run, %d warm-up runs, %d measured runs, seeds %d .. %d%n%n",
                    ticks, warmup, runs, seed, seed + runs - 1);
            out.printf("  %-12s %7s %7s  %-12s %12s %12s %12s%n", "Family", "Places", "Trans.", "Finder",
                    "Median ns", "Min ns", "Max ns");
            out.flush();
        }

        List<Point> points = new ArrayList<>();
        for (PetriNet net : nets) {
            for (FinderType finder : finders) {
                Point point = benchmark.measure(net, finder);
                points.add(point);
                if (!output.quiet) {
                    out.printf(Locale.ROOT, "  %-12s %7d %7d  %-12s %12.2f %12.2f %12.2f%n", family,
                            net.getPlaces().size(), net.getTransitions().size(), name(finder),
                            point.median(), point.min(), point.max());
                    out.flush();
                }
            }
        }
        if (output.outDir != null) {
            write(points);
        }
        out.flush();
    }

    private List<PetriNet> nets() {
        List<PetriNet> nets = new ArrayList<>();
        switch (family) {
            case "state-machine" -> {
                if (places.isEmpty() || transitions.isEmpty()) {
                    throw new IllegalArgumentException("state-machine needs --places and --transitions");
                }
                for (int p : places) {
                    for (int t : transitions) {
                        nets.add(NetGenerators.stateMachine(p, t, netSeed));
                    }
                }
            }
            case "queue-chain" -> {
                if (nodes.isEmpty()) {
                    throw new IllegalArgumentException("queue-chain needs --nodes");
                }
                for (int n : nodes) {
                    nets.add(NetGenerators.queueChain(n, servers));
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unknown family '" + family + "', expected state-machine or queue-chain");
        }
        return nets;
    }

    private void write(List<Point> points) {
        CsvWriter measurements = new CsvWriter("family", "places", "transitions", "finder", "run", "seed",
                "ticks", "nanos", "ns_per_tick");
        CsvWriter summary = new CsvWriter("family", "places", "transitions", "finder", "runs",
                "median_ns_per_tick", "mean_ns_per_tick", "std_ns_per_tick", "min_ns_per_tick", "max_ns_per_tick");
        for (Point point : points) {
            int p = point.net().getPlaces().size();
            int t = point.net().getTransitions().size();
            String finder = name(point.finder());
            for (Measurement m : point.measurements()) {
                measurements.row(family, p, t, finder, m.run(), m.seed(), m.ticks(), m.nanos(), m.nanosPerTick());
            }
            summary.row(family, p, t, finder, point.measurements().size(), point.median(), point.mean(),
                    point.standardDeviation(), point.min(), point.max());
        }
        measurements.write(output.outDir.resolve("measurements.csv"));
        summary.write(output.outDir.resolve("summary.csv"));
    }

    private static String name(FinderType finder) {
        return finder.name().toLowerCase(Locale.ROOT);
    }
}
