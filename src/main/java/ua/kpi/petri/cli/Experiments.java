package ua.kpi.petri.cli;

import ua.kpi.petri.analysis.ReachabilityComparison;
import ua.kpi.petri.analysis.ReachabilityResult;
import ua.kpi.petri.analysis.SimulatedReachability;
import ua.kpi.petri.generator.NetGenerators;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class Experiments {

    static final long SEED = 1;
    static final int TICKS = 1000;
    static final int SATURATION_WINDOW = 100;

    static final String VERIFICATION = "verification";
    static final String BENCHMARK = "benchmark";
    static final String REACHABILITY = "4.3-reachability";
    static final String ACTIVITY = "4.4-activity";

    record NetSpec(String name, Supplier<PetriNet> net) {
    }

    /** One invocation of the benchmark command. */
    record BenchmarkRun(String title, String dir, List<String> args) {
    }

    private final Path out;
    private final PrintWriter console;
    private final StepRunner runner;

    Experiments(Path out, PrintWriter console) {
        this.out = out;
        this.console = console;
        this.runner = new StepRunner(console);
    }

    static List<NetSpec> verificationNets() {
        List<NetSpec> nets = new ArrayList<>();
        for (String name : List.of("lecture7-abc", "shared-resource", "partial-resource", "conflict-chain")) {
            nets.add(example(name));
        }
        for (int n = 3; n <= 5; n++) {
            nets.add(philosophers(n, false));
        }
        nets.add(philosophers(5, true));
        nets.add(queueChain(2));
        nets.add(example("unbounded-queue"));
        nets.add(example("dead-transition"));
        return nets;
    }

    static List<NetSpec> reachabilityNets() {
        List<NetSpec> nets = new ArrayList<>();
        for (int n = 3; n <= 12; n++) {
            nets.add(philosophers(n, false));
            nets.add(philosophers(n, true));
        }
        for (int p = 4; p <= 8; p++) {
            nets.add(stateMachine(p));
        }
        return nets;
    }

    static List<NetSpec> activityNets() {
        List<NetSpec> nets = new ArrayList<>();
        for (boolean bothAtOnce : List.of(false, true)) {
            for (int n : List.of(5, 10, 12)) {
                nets.add(philosophers(n, bothAtOnce));
            }
        }
        nets.add(stateMachine(8));
        nets.add(queueChain(2));
        nets.add(example("dead-transition"));
        return nets;
    }

    /** Series A, B and C with {@code warmup} runs before every point. */
    static List<BenchmarkRun> benchmarkRuns(int warmup) {
        List<BenchmarkRun> runs = new ArrayList<>();
        String w = Integer.toString(warmup);
        runs.add(new BenchmarkRun("Series A: effect of T (P = 100)", "series-a", List.of("--family", "state-machine",
                "--places", "100", "--transitions", "100,200,400,800,1600,3200,6400,12800", "--warmup", w, "-r", "20")));
        runs.add(new BenchmarkRun("Series B: effect of P (T = 6400)", "series-b", List.of("--family", "state-machine",
                "--places", "100,200,400,800,1600,3200,6400", "--transitions", "6400", "--warmup", w, "-r", "20")));
        runs.add(new BenchmarkRun("Series C: queue chain", "series-c", List.of("--family", "queue-chain",
                "-n", "10,20,50,100,200,500,1000,2000", "--warmup", w, "-r", "20")));
        return runs;
    }

    /** Fails before anything runs if an experiment would write over earlier results. */
    void requireFresh(String... folders) {
        for (String folder : folders) {
            Path dir = out.resolve(folder);
            if (Files.exists(dir)) {
                throw new IllegalStateException(dir + " already exists: move it away or choose another --out");
            }
        }
    }

    void environment() {
        section("Conditions of the experiments");
        List<String> lines = EnvironmentReport.lines();
        lines.forEach(console::println);
        write(out.resolve("environment.txt"), lines);
    }

    void verification(List<NetSpec> nets) {
        Path root = out.resolve(VERIFICATION);
        for (NetSpec spec : nets) {
            section("Verification: " + spec.name());
            String net = save(spec, root.resolve("nets")).toString();
            Path dir = root.resolve(spec.name());
            runner.run(dir.resolve("1-structure.txt"), new InfoCommand(), net);
            runner.run(dir.resolve("2-single-run.txt"), new SimulateCommand(), net, "-t", "30", "-s", seed(),
                    "-p", dir.resolve("2-protocol.txt").toString(), "-o", dir.resolve("single-run").toString());
            runner.run(dir.resolve("3-series.txt"), new SimulateCommand(), net, "-r", "auto", "-t", ticks(),
                    "-s", seed(), "-o", dir.resolve("series").toString());
            runner.run(dir.resolve("4-tree.txt"), new ReachabilityCommand(), net, "--exact",
                    "-o", dir.resolve("tree").toString());
            runner.run(dir.resolve("5-simulated-reachability.txt"), new ReachabilityCommand(), net, "-t", ticks(),
                    "-s", seed(), "--compare-exact", "-o", dir.resolve("simulated-reachability").toString());
            runner.run(dir.resolve("6-activity.txt"), new ActivityCommand(), net, "-r", "auto", "-t", ticks(),
                    "-s", seed(), "-o", dir.resolve("activity").toString());
        }
    }

    /** Each net is simulated until saturation and compared with its reachability tree; summary.csv collects them. */
    void reachability(List<NetSpec> nets) {
        Path root = out.resolve(REACHABILITY);
        CsvWriter csv = new CsvWriter("net", "places", "transitions", "tree_markings", "markings_met",
                "met_in_tree", "runs", "saturated", "seconds");
        List<String> table = new ArrayList<>();
        table.add(String.format("%-30s %7s %7s %10s %10s %10s %8s %10s %8s", "Net", "Places", "Trans.",
                "Tree", "Met", "In tree", "Runs", "Saturated", "Seconds"));
        for (NetSpec spec : nets) {
            section("4.3 Reachable markings: " + spec.name());
            String net = save(spec, root.resolve("nets")).toString();
            Path dir = root.resolve(spec.name());
            ReachabilityCommand command = new ReachabilityCommand();
            long start = System.nanoTime();
            runner.run(dir.resolve("saturation.txt"), command, net, "-t", ticks(), "-s", seed(),
                    "--until-saturated", Integer.toString(SATURATION_WINDOW), "--max-runs", "100000",
                    "--compare-exact", "--max-nodes", "5000000", "-o", dir.toString());
            double seconds = (System.nanoTime() - start) / 1e9;

            SimulatedReachability.Result simulated = command.simulatedResult();
            ReachabilityResult tree = command.exactResult();
            PetriNet structure = tree.net();
            Integer treeMarkings = tree.complete() ? tree.markings().size() : null;
            Integer inTree = tree.complete() ? ReachabilityComparison.of(simulated, tree).inTree() : null;
            boolean saturated = simulated.runsWithoutNew() >= SATURATION_WINDOW;
            csv.row(spec.name(), structure.getPlaces().size(), structure.getTransitions().size(), treeMarkings,
                    simulated.markings().size(), inTree, simulated.runCount(), saturated, seconds);
            table.add(String.format(Locale.ROOT, "%-30s %7d %7d %10s %10d %10s %8d %10s %8.1f", spec.name(),
                    structure.getPlaces().size(), structure.getTransitions().size(), orDash(treeMarkings),
                    simulated.markings().size(), orDash(inTree), simulated.runCount(), saturated ? "yes" : "no",
                    seconds));
        }
        csv.write(root.resolve("summary.csv"));
        write(root.resolve("summary.txt"), table);
        section("4.3 Summary");
        table.forEach(console::println);
    }

    void activity(List<NetSpec> nets) {
        Path root = out.resolve(ACTIVITY);
        for (NetSpec spec : nets) {
            section("4.4 Activity: " + spec.name());
            String net = save(spec, root.resolve("nets")).toString();
            Path dir = root.resolve(spec.name());
            runner.run(dir.resolve("activity.txt"), new ActivityCommand(), net, "-r", "auto", "-t", ticks(),
                    "-s", seed(), "-o", dir.toString());
        }
    }

    /** The runs follow one another, so that they do not share the processor. */
    void benchmark(List<BenchmarkRun> runs) {
        Path root = out.resolve(BENCHMARK);
        for (BenchmarkRun run : runs) {
            section("4.2 " + run.title());
            Path dir = root.resolve(run.dir());
            List<String> args = new ArrayList<>(run.args());
            args.addAll(List.of("-o", dir.toString()));
            runner.run(dir.resolve("console.txt"), new BenchmarkCommand(), args.toArray(String[]::new));
        }
    }

    private static NetSpec example(String name) {
        return new NetSpec(name, () -> {
            try (InputStream in = Experiments.class.getResourceAsStream("/examples/" + name + ".json")) {
                if (in == null) {
                    throw new IllegalStateException("Example net " + name + " is missing from the jar");
                }
                return NetJson.read(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static NetSpec philosophers(int count, boolean bothAtOnce) {
        return new NetSpec("philosophers-" + count + (bothAtOnce ? "-both-at-once" : ""),
                () -> NetGenerators.philosophers(count, bothAtOnce));
    }

    private static NetSpec stateMachine(int places) {
        return new NetSpec("state-machine-" + places, () -> NetGenerators.stateMachine(places, 3 * places, SEED));
    }

    private static NetSpec queueChain(int nodes) {
        return new NetSpec("queue-chain-" + nodes, () -> NetGenerators.queueChain(nodes, 1));
    }

    private static Path save(NetSpec spec, Path dir) {
        Path file = dir.resolve(spec.name() + ".json");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        NetJson.write(spec.net().get(), file);
        return file;
    }

    private static void write(Path file, List<String> lines) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void section(String title) {
        console.println();
        console.println("=== " + title);
        console.flush();
    }

    private static String orDash(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private static String seed() {
        return Long.toString(SEED);
    }

    private static String ticks() {
        return Integer.toString(TICKS);
    }
}
