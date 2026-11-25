package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import ua.kpi.petri.io.CsvWriter;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.observer.EventProtocol;
import ua.kpi.petri.observer.FiringCounter;
import ua.kpi.petri.observer.MarkingStatistics;
import ua.kpi.petri.observer.MarkingStatistics.PlaceStatistics;
import ua.kpi.petri.simulation.NaiveEnabledTransitionsFinder;
import ua.kpi.petri.simulation.SimulationResult;
import ua.kpi.petri.simulation.Simulator;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

@Command(name = "simulate", mixinStandardHelpOptions = true, description = "Run one simulation of a net: event protocol and marking statistics.")
final class SimulateCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "NET", description = "Net definition file (JSON).")
    private Path netFile;

    @Option(names = { "--ticks", "-t" }, defaultValue = "1000", description = "Tick limit (default: ${DEFAULT-VALUE}).")
    private int ticks;

    @Option(names = { "--seed", "-s" }, description = "Random seed; a random one is chosen and printed if omitted.")
    private Long seed;

    @Option(names = { "--protocol", "-p" }, paramLabel = "FILE|-", description = "Write the event protocol to FILE or '-' for stdout.")
    private String protocol;

    @Option(names = { "--out", "-o" }, paramLabel = "DIR", description = "Write CSV results to DIR.")
    private Path outDir;

    @Option(names = { "--quiet", "-q" }, description = "Do not print result tables.")
    private boolean quiet;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        if (ticks < 0) {
            throw new IllegalArgumentException("--ticks must not be negative");
        }
        PetriNet net = NetJson.read(netFile);
        long actualSeed = seed != null ? seed : RandomGenerator.getDefault().nextLong();
        PrintWriter out = spec.commandLine().getOut();

        MarkingStatistics statistics = new MarkingStatistics();
        FiringCounter firings = new FiringCounter();
        Simulator simulator = new Simulator(net, new NaiveEnabledTransitionsFinder(net), newRandom(actualSeed))
                .addListener(statistics)
                .addListener(firings);

        SimulationResult result;
        if (protocol == null) {
            result = simulator.run(ticks);
        } else if (protocol.equals("-")) {
            simulator.addListener(new EventProtocol(out));
            result = simulator.run(ticks);
            out.println();
        } else {
            try (PrintWriter file = new PrintWriter(Files.newBufferedWriter(Path.of(protocol), StandardCharsets.UTF_8))) {
                simulator.addListener(new EventProtocol(file));
                result = simulator.run(ticks);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write protocol " + protocol, e);
            }
        }

        if (!quiet) {
            printTables(out, net, actualSeed, result, statistics, firings.getCounts());
        }
        if (outDir != null) {
            writeCsv(statistics, firings.getCounts());
        }
        out.flush();
    }

    static RandomGenerator newRandom(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    private static void printTables(PrintWriter out, PetriNet net, long seed, SimulationResult result,
                                    MarkingStatistics statistics, Map<String, Long> firings) {
        out.println(net);
        out.println("Seed:   " + seed);
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

    private void writeCsv(MarkingStatistics statistics, Map<String, Long> firings) {
        CsvWriter places = new CsvWriter("place", "mean", "min", "max");
        for (PlaceStatistics s : statistics.getStatistics()) {
            places.row(s.place(), s.mean(), s.min(), s.max());
        }
        places.write(outDir.resolve("place-statistics.csv"));

        CsvWriter transitions = new CsvWriter("transition", "firings");
        firings.forEach(transitions::row);
        transitions.write(outDir.resolve("transition-firings.csv"));
    }
}
