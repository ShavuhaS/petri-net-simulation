package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import ua.kpi.petri.analysis.MarkingStatisticsAnalysis;
import ua.kpi.petri.analysis.MarkingStatisticsAnalysis.PlaceSummary;
import ua.kpi.petri.analysis.MarkingStatisticsAnalysis.RunSummary;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.model.Place;
import ua.kpi.petri.observer.EventProtocol;
import ua.kpi.petri.observer.FiringCounter;
import ua.kpi.petri.observer.MarkingStatistics;
import ua.kpi.petri.observer.MarkingStatistics.PlaceStatistics;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.SimulationSeries;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@Command(name = "simulate", mixinStandardHelpOptions = true,
        description = "Simulate a net: event protocol and marking statistics of one run, "
                + "or marking statistics over a series of runs.")
final class SimulateCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "NET", description = "Net definition file (JSON).")
    private Path netFile;

    @Mixin
    private RunOptions runOptions;

    @Mixin
    private OutputOptions output;

    @Option(names = {"--runs", "-r"}, defaultValue = "1", converter = RunCount.Converter.class, paramLabel = "N|auto",
            description = "Number of runs, or 'auto' to choose it by Chebyshev's inequality after a pilot series "
                    + "(default: ${DEFAULT-VALUE}).")
    private RunCount runs;

    @Option(names = "--pilot-runs", defaultValue = "20", description = "Runs of the pilot series for --runs auto (default: ${DEFAULT-VALUE}).")
    private int pilotRuns;

    @Option(names = {"--epsilon", "-e"}, defaultValue = "0.05",
            description = "Required accuracy of the mean marking, in tokens, for --runs auto (default: ${DEFAULT-VALUE}).")
    private double epsilon;

    @Option(names = "--max-runs", defaultValue = "10000", description = "Upper limit of runs for --runs auto (default: ${DEFAULT-VALUE}).")
    private int maxRuns;

    @Option(names = {"--protocol", "-p"}, paramLabel = "FILE|-",
            description = "Write the event protocol of a single run to FILE or '-' for stdout.")
    private String protocol;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        PetriNet net = NetJson.read(netFile);
        SimulationSeries series = runOptions.series(net);
        if (!runs.auto() && runs.count() == 1) {
            singleRun(series);
        } else {
            if (protocol != null) {
                throw new IllegalArgumentException("--protocol is written for a single run only");
            }
            seriesOfRuns(series);
        }
        spec.commandLine().getOut().flush();
    }

    private void singleRun(SimulationSeries series) {
        PrintWriter out = spec.commandLine().getOut();
        MarkingStatistics statistics = new MarkingStatistics();
        FiringCounter firings = new FiringCounter();

        SimulationResult result;
        if (protocol == null) {
            result = series.run(0, statistics, firings);
        } else if (protocol.equals("-")) {
            result = series.run(0, statistics, firings, new EventProtocol(out));
            out.println();
        } else {
            try (PrintWriter file = new PrintWriter(Files.newBufferedWriter(Path.of(protocol), StandardCharsets.UTF_8))) {
                result = series.run(0, statistics, firings, new EventProtocol(file));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write protocol " + protocol, e);
            }
        }

        if (!output.quiet) {
            printSingleRun(out, series, result, statistics, firings.getCounts());
        }
        if (output.outDir != null) {
            CsvWriter places = new CsvWriter("place", "mean", "min", "max");
            for (PlaceStatistics s : statistics.getStatistics()) {
                places.row(s.place(), s.mean(), s.min(), s.max());
            }
            places.write(output.outDir.resolve("place-statistics.csv"));

            CsvWriter transitions = new CsvWriter("transition", "firings");
            firings.getCounts().forEach(transitions::row);
            transitions.write(output.outDir.resolve("transition-firings.csv"));
        }
    }

    private static void printSingleRun(PrintWriter out, SimulationSeries series, SimulationResult result,
                                       MarkingStatistics statistics, Map<String, Long> firings) {
        out.println(series.net());
        out.println("Seed:   " + series.seedOf(0));
        out.printf("Ticks:  %d (%s)%n", result.ticks(),
                result.deadlocked() ? "stopped by deadlock" : "tick limit reached");
        out.println("Final marking: " + result.finalMarking());
        out.println();

        out.printf("Marking statistics over ticks 0..%d:%n", result.ticks());
        out.printf("  %-20s %10s %6s %6s%n", "Place", "Mean", "Min", "Max");
        for (PlaceStatistics s : statistics.getStatistics()) {
            out.printf(Locale.ROOT, "  %-20s %10.4f %6d %6d%n", s.place(), s.mean(), s.min(), s.max());
        }
        out.println();

        out.println("Transition firings:");
        out.printf("  %-20s %10s%n", "Transition", "Firings");
        firings.forEach((name, count) -> out.printf("  %-20s %10d%n", name, count));
    }

    private void seriesOfRuns(SimulationSeries series) {
        double confidence = runOptions.confidence();
        MarkingStatisticsAnalysis analysis = new MarkingStatisticsAnalysis(series, confidence);
        MarkingStatisticsAnalysis.Result result = runs.auto()
                ? analysis.runAuto(pilotRuns, epsilon, maxRuns)
                : analysis.runFixed(runs.count());

        if (!output.quiet) {
            printSeries(spec.commandLine().getOut(), series, result);
        }
        if (output.outDir != null) {
            writeSeriesCsv(series.net(), result);
        }
    }

    private void printSeries(PrintWriter out, SimulationSeries series, MarkingStatisticsAnalysis.Result result) {
        int n = result.runCount();
        out.println(series.net());
        if (result.requiredRuns() < 0) {
            out.printf("Runs:   %d%n", n);
        } else {
            out.printf(Locale.ROOT, "Runs:   %d (pilot series %d; Chebyshev's inequality requires %d for epsilon = %s",
                    n, result.pilotRuns(), result.requiredRuns(), epsilon);
            out.println(result.requiredRuns() > maxRuns ? ", limited by --max-runs)" : ")");
        }
        out.printf("Seeds:  %d .. %d%n", series.seedOf(0), series.seedOf(n - 1));
        out.printf("Ticks:  limit %d per run; %d of %d runs ended in a deadlock%n",
                series.maxTicks(), result.deadlockRuns(), n);
        out.println();

        out.printf(Locale.ROOT, "Mean marking over %d runs (error by Chebyshev's inequality at confidence %s):%n",
                n, result.confidence());
        out.printf("  %-20s %10s %10s %10s %6s %6s%n", "Place", "Mean", "+/- error", "Variance", "Min", "Max");
        for (PlaceSummary s : result.places()) {
            out.printf(Locale.ROOT, "  %-20s %10.4f %10s %10s %6d %6d%n", s.place(), s.mean(),
                    format(s.error()), format(s.variance()), s.min(), s.max());
        }
    }

    private static String format(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.4f", value);
    }

    private void writeSeriesCsv(PetriNet net, MarkingStatisticsAnalysis.Result result) {
        CsvWriter places = new CsvWriter("place", "mean", "error", "variance", "min", "max", "runs", "confidence");
        for (PlaceSummary s : result.places()) {
            places.row(s.place(), s.mean(), s.error(), s.variance(), s.min(), s.max(), result.runCount(),
                    result.confidence());
        }
        places.write(output.outDir.resolve("place-statistics.csv"));

        String[] header = new String[net.getPlaces().size() + 4];
        header[0] = "run";
        header[1] = "seed";
        header[2] = "ticks";
        header[3] = "deadlock";
        for (Place place : net.getPlaces()) {
            header[place.getIndex() + 4] = "mean_" + place.getName();
        }
        CsvWriter runsCsv = new CsvWriter(header);
        for (RunSummary run : result.runs()) {
            Object[] row = new Object[header.length];
            row[0] = run.run();
            row[1] = run.seed();
            row[2] = run.result().ticks();
            row[3] = run.result().deadlocked();
            for (int p = 0; p < run.means().length; p++) {
                row[p + 4] = run.means()[p];
            }
            runsCsv.row(row);
        }
        runsCsv.write(output.outDir.resolve("runs.csv"));
    }
}
