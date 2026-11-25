package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import ua.kpi.petri.analysis.ActivityAnalysis;
import ua.kpi.petri.analysis.ActivityAnalysis.TransitionActivity;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.simulation.SimulationSeries;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Locale;

@Command(name = "activity", mixinStandardHelpOptions = true,
        description = "Judge the liveness level of transitions from a series of runs.")
final class ActivityCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "NET", description = "Net definition file (JSON).")
    private Path netFile;

    @Mixin
    private RunOptions runOptions;

    @Mixin
    private OutputOptions output;

    @Option(names = {"--runs", "-r"}, defaultValue = "auto", converter = RunCount.Converter.class, paramLabel = "N|auto",
            description = "Number of runs, or 'auto' to estimate every share within --epsilon "
                    + "by Chebyshev's inequality (default: ${DEFAULT-VALUE}).")
    private RunCount runs;

    @Option(names = {"--epsilon", "-e"}, defaultValue = "0.05",
            description = "Required accuracy of the share of runs in which a transition fires, for --runs auto "
                    + "(default: ${DEFAULT-VALUE}).")
    private double epsilon;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        PetriNet net = NetJson.read(netFile);
        SimulationSeries series = runOptions.series(net);
        double confidence = runOptions.confidence();
        int runCount = runs.auto() ? ActivityAnalysis.requiredRuns(epsilon, confidence) : runs.count();
        ActivityAnalysis.Result result = ActivityAnalysis.run(series, runCount, confidence);

        if (!output.quiet) {
            print(spec.commandLine().getOut(), series, result);
        }
        if (output.outDir != null) {
            CsvWriter csv = new CsvWriter("transition", "runs_fired", "runs", "share", "share_error",
                    "mean_firings", "level", "fire_probability_bound", "confidence");
            for (TransitionActivity t : result.transitions()) {
                csv.row(t.transition(), t.runsFired(), result.runs(), t.share(), t.shareError(), t.meanFirings(),
                        t.fired() ? ">=1" : "0", t.fired() ? null : result.deadBound(), result.confidence());
            }
            csv.write(output.outDir.resolve("activity.csv"));
        }
        spec.commandLine().getOut().flush();
    }

    private void print(PrintWriter out, SimulationSeries series, ActivityAnalysis.Result result) {
        int n = result.runs();
        out.println(series.net());
        out.printf("Runs:   %d%s; seeds %d .. %d, tick limit %d%n", n,
                runs.auto() ? ", required by Chebyshev's inequality for epsilon = " + epsilon : "",
                series.seedOf(0), series.seedOf(n - 1), series.maxTicks());
        out.println();

        out.printf(Locale.ROOT, "Share of runs in which a transition fired (error at confidence %s):%n",
                result.confidence());
        out.printf("  %-20s %12s %8s %10s %12s   %s%n", "Transition", "Runs fired", "Share", "+/- error",
                "Firings/run", "Liveness level");
        for (TransitionActivity t : result.transitions()) {
            out.printf(Locale.ROOT, "  %-20s %12s %8.4f %10.4f %12.2f   %s%n", t.transition(),
                    t.runsFired() + "/" + n, t.share(), t.shareError(), t.meanFirings(),
                    t.fired() ? ">= 1" : "0");
        }
        out.println();
        if (result.transitions().stream().allMatch(TransitionActivity::fired)) {
            out.println("Level 0: none, every transition fired at least once.");
        } else {
            out.printf(Locale.ROOT, "Level 0: the transition did not fire in any of %d runs; with confidence %s "
                    + "its probability to fire in a run is below %.4f.%n", n, result.confidence(), result.deadBound());
        }
        if (result.level4Excluded()) {
            out.printf("Level 4: excluded for every transition, %d of %d runs reached a dead marking.%n",
                    result.deadlockRuns(), n);
        } else {
            out.println("Level 4: no run reached a dead marking, so it is not excluded.");
        }
    }
}
