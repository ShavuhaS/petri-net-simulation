package ua.kpi.petri.cli;

import picocli.CommandLine.Option;
import ua.kpi.petri.model.PetriNet;
import ua.kpi.petri.simulation.FinderType;
import ua.kpi.petri.simulation.SimulationSeries;

import java.util.random.RandomGenerator;

/** Options shared by commands that run simulations. */
final class RunOptions {

    @Option(names = {"--ticks", "-t"}, defaultValue = "1000", description = "Tick limit of one run (default: ${DEFAULT-VALUE}).")
    private int ticks;

    @Option(names = {"--seed", "-s"},
            description = "Seed of the first run; run i uses seed + i. A random one is chosen and printed if omitted.")
    private Long seed;

    @Option(names = {"--confidence", "-c"}, defaultValue = "0.95",
            description = "Confidence probability for statistical estimates (default: ${DEFAULT-VALUE}).")
    private double confidence;

    @Option(names = "--finder", defaultValue = "naive",
            description = "Way of finding enabled transitions: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private FinderType finder;

    private Long chosenSeed;

    long seed() {
        if (chosenSeed == null) {
            chosenSeed = seed != null ? seed : RandomGenerator.getDefault().nextLong();
        }
        return chosenSeed;
    }

    double confidence() {
        if (!(confidence > 0 && confidence < 1)) {
            throw new IllegalArgumentException("--confidence must be between 0 and 1");
        }
        return confidence;
    }

    SimulationSeries series(PetriNet net) {
        if (ticks < 0) {
            throw new IllegalArgumentException("--ticks must not be negative");
        }
        return new SimulationSeries(net, seed(), ticks, finder);
    }
}
