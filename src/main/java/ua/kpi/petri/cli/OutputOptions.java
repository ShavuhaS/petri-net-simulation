package ua.kpi.petri.cli;

import picocli.CommandLine.Option;

import java.nio.file.Path;

/** Options shared by commands that print tables and write CSV files. */
final class OutputOptions {

    @Option(names = {"--out", "-o"}, paramLabel = "DIR", description = "Write CSV results to DIR.")
    Path outDir;

    @Option(names = {"--quiet", "-q"}, description = "Do not print result tables.")
    boolean quiet;
}
