package ua.kpi.petri.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "petri",
        mixinStandardHelpOptions = true,
        version = "petri-net-simulation 0.1.0",
        description = "Simulation and analysis of classical Petri nets.",
        subcommands = {InfoCommand.class, SimulateCommand.class, ReachabilityCommand.class, ActivityCommand.class,
                GenerateCommand.class, CommandLine.HelpCommand.class})
public final class PetriCli implements Runnable {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        System.exit(newCommandLine().execute(args));
    }

    static CommandLine newCommandLine() {
        return new CommandLine(new PetriCli())
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    cmd.getErr().println("Error: " + ex.getMessage());
                    return 1;
                });
    }
}
