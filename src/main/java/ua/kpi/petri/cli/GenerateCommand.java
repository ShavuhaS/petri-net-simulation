package ua.kpi.petri.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;
import ua.kpi.petri.generator.NetGenerators;
import ua.kpi.petri.io.NetJson;
import ua.kpi.petri.model.PetriNet;

import java.io.PrintWriter;
import java.nio.file.Path;

@Command(name = "generate", mixinStandardHelpOptions = true,
        description = "Generate a net of a given family and size and write it as JSON.",
        subcommands = {GenerateCommand.StateMachine.class, GenerateCommand.QueueChain.class,
                GenerateCommand.Philosophers.class})
final class GenerateCommand implements Runnable {

    @Option(names = {"--out", "-o"}, paramLabel = "FILE", scope = ScopeType.INHERIT,
            description = "Write the net to FILE instead of the standard output.")
    private Path outFile;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    private void write(PetriNet net) {
        PrintWriter out = spec.commandLine().getOut();
        if (outFile == null) {
            out.println(NetJson.toJson(net));
        } else {
            NetJson.write(net, outFile);
            out.println(net + " written to " + outFile);
        }
        out.flush();
    }

    @Command(name = "state-machine", mixinStandardHelpOptions = true,
            description = "Cycle through all places plus random one-input, one-output transitions; one token per place.")
    static final class StateMachine implements Runnable {

        @ParentCommand
        private GenerateCommand parent;

        @Option(names = "--places", required = true, description = "Number of places, at least 2.")
        private int places;

        @Option(names = "--transitions", required = true, description = "Number of transitions, at least --places.")
        private int transitions;

        @Option(names = {"--seed", "-s"}, defaultValue = "1", description = "Seed of the random transitions (default: ${DEFAULT-VALUE}).")
        private long seed;

        @Override
        public void run() {
            parent.write(NetGenerators.stateMachine(places, transitions, seed));
        }
    }

    @Command(name = "queue-chain", mixinStandardHelpOptions = true,
            description = "Generator loop followed by a chain of queueing nodes.")
    static final class QueueChain implements Runnable {

        @ParentCommand
        private GenerateCommand parent;

        @Option(names = {"--nodes", "-n"}, required = true, description = "Number of queueing nodes.")
        private int nodes;

        @Option(names = "--servers", defaultValue = "1", description = "Servers in each node (default: ${DEFAULT-VALUE}).")
        private int servers;

        @Override
        public void run() {
            parent.write(NetGenerators.queueChain(nodes, servers));
        }
    }

    @Command(name = "philosophers", mixinStandardHelpOptions = true,
            description = "Dining philosophers; by default each takes the left stick first and the net can deadlock.")
    static final class Philosophers implements Runnable {

        @ParentCommand
        private GenerateCommand parent;

        @Option(names = {"--count", "-n"}, required = true, description = "Number of philosophers, at least 2.")
        private int count;

        @Option(names = "--both-at-once", description = "Take both sticks in one transition: the net cannot deadlock.")
        private boolean bothAtOnce;

        @Override
        public void run() {
            parent.write(NetGenerators.philosophers(count, bothAtOnce));
        }
    }
}
