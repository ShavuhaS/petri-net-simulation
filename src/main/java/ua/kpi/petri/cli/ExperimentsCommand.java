package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;

@Command(name = "experiments", mixinStandardHelpOptions = true,
        description = {"Run the experiments of the report and keep their output for it.",
                "Every experiment refuses to write over an existing folder of results."},
        subcommands = {ExperimentsCommand.Verification.class, ExperimentsCommand.Benchmark.class,
                ExperimentsCommand.Reachability.class, ExperimentsCommand.Activity.class, ExperimentsCommand.Proposal.class,
                ExperimentsCommand.All.class})
final class ExperimentsCommand implements Runnable {

    @Option(names = {"--out", "-o"}, defaultValue = "results", paramLabel = "DIR", scope = ScopeType.INHERIT,
            description = "Folder of the results (default: ${DEFAULT-VALUE}).")
    private Path out;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    private Experiments start(String... folders) {
        PrintWriter console = spec.commandLine().getOut();
        Experiments experiments = new Experiments(out, console);
        experiments.requireFresh(folders);
        experiments.environment();
        return experiments;
    }

    private void finish(long start) {
        PrintWriter console = spec.commandLine().getOut();
        console.println();
        console.printf(Locale.ROOT, "Done in %.1f min; results in %s%n", (System.nanoTime() - start) / 60e9,
                out.toAbsolutePath());
        console.flush();
    }

    @Command(name = "verification", mixinStandardHelpOptions = true,
            description = "Verification of the model: every net through the same six steps (subsection 3.7).")
    static final class Verification implements Runnable {

        @ParentCommand
        private ExperimentsCommand parent;

        @Override
        public void run() {
            long start = System.nanoTime();
            parent.start(Experiments.VERIFICATION).verification(Experiments.verificationNets());
            parent.finish(start);
        }
    }

    @Command(name = "benchmark", mixinStandardHelpOptions = true,
            description = {"Time of one simulation step: series A, B and C (subsection 4.2).",
                    "Run it in a fresh JVM with a fixed heap:",
                    "  java -Xms4g -Xmx4g -jar petri-net-simulation.jar experiments benchmark"})
    static final class Benchmark implements Runnable {

        @ParentCommand
        private ExperimentsCommand parent;

        @Option(names = "--warmup", defaultValue = "5",
                description = "Warm-up runs before every point of the series (default: ${DEFAULT-VALUE}).")
        private int warmup;

        @Override
        public void run() {
            long start = System.nanoTime();
            parent.start(Experiments.BENCHMARK).benchmark(Experiments.benchmarkRuns(warmup));
            parent.finish(start);
        }
    }

    @Command(name = "reachability", mixinStandardHelpOptions = true,
            description = "Set of reachable markings of growing nets against the reachability tree (subsection 4.3).")
    static final class Reachability implements Runnable {

        @ParentCommand
        private ExperimentsCommand parent;

        @Override
        public void run() {
            long start = System.nanoTime();
            parent.start(Experiments.REACHABILITY).reachability(Experiments.reachabilityNets());
            parent.finish(start);
        }
    }

    @Command(name = "activity", mixinStandardHelpOptions = true,
            description = "Liveness of transitions from series of runs (subsection 4.4).")
    static final class Activity implements Runnable {

        @ParentCommand
        private ExperimentsCommand parent;

        @Override
        public void run() {
            long start = System.nanoTime();
            parent.start(Experiments.ACTIVITY).activity(Experiments.activityNets());
            parent.finish(start);
        }
    }

    @Command(name = "proposal", mixinStandardHelpOptions = true,
            description = {"Series of runs with a full scan and with the incremental search (subsection 5.2).",
                    "Writes the conditions of the experiment into its own folder. On a processor with performance",
                    "and efficiency cores keep it on the performance ones (Linux, i5-12450H: CPUs 0-7):",
                    "  taskset -c 0-7 java -Xms4g -Xmx4g -jar petri-net-simulation.jar experiments proposal"})
    static final class Proposal implements Runnable {

        @ParentCommand
        private ExperimentsCommand parent;

        @Override
        public void run() {
            long start = System.nanoTime();
            PrintWriter console = parent.spec.commandLine().getOut();
            Experiments experiments = new Experiments(parent.out, console);
            experiments.requireFresh(Experiments.PROPOSAL);
            experiments.environment(parent.out.resolve(Experiments.PROPOSAL));
            experiments.proposal(Experiments.proposalNets());
            parent.finish(start);
        }
    }

    @Command(name = "all", mixinStandardHelpOptions = true,
            description = {"All experiments: the benchmark first, then verification, reachable markings and activity.",
                    "Takes about half an hour, mostly the benchmark. Run it with as little background load as possible "
                    + "and a fixed heap:",
                    "  java -Xms4g -Xmx4g -jar petri-net-simulation.jar experiments all"})
    static final class All implements Runnable {

        @ParentCommand
        private ExperimentsCommand parent;

        @Option(names = "--warmup", defaultValue = "5",
                description = "Warm-up runs before every point of the benchmark series (default: ${DEFAULT-VALUE}).")
        private int warmup;

        @Override
        public void run() {
            long start = System.nanoTime();
            Experiments experiments = parent.start(Experiments.VERIFICATION, Experiments.REACHABILITY,
                    Experiments.ACTIVITY, Experiments.BENCHMARK, Experiments.PROPOSAL);
            experiments.benchmark(Experiments.benchmarkRuns(warmup));
            experiments.verification(Experiments.verificationNets());
            experiments.reachability(Experiments.reachabilityNets());
            experiments.activity(Experiments.activityNets());
            experiments.proposal(Experiments.proposalNets());
            parent.finish(start);
        }
    }
}
