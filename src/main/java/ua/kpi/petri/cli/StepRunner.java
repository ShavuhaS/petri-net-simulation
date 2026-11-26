package ua.kpi.petri.cli;

import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a command of this CLI in the current process. Its output goes both to the console and to a log file
 * that starts with the command line, so the file can be quoted in the report as it is.
 */
final class StepRunner {

    private final PrintWriter console;

    StepRunner(PrintWriter console) {
        this.console = console;
    }

    void run(Path log, Object command, String... args) {
        CommandLine commandLine = PetriCli.configure(new CommandLine(command));
        String line = "$ petri " + commandLine.getCommandName() + " " + String.join(" ", args);
        try {
            Files.createDirectories(log.toAbsolutePath().getParent());
            try (Writer file = Files.newBufferedWriter(log, StandardCharsets.UTF_8)) {
                PrintWriter both = new PrintWriter(new TeeWriter(console, file));
                both.println(line);
                both.println();
                both.flush();
                commandLine.setOut(both);
                commandLine.setErr(both);
                int exitCode = commandLine.execute(args);
                both.flush();
                if (exitCode != 0) {
                    throw new IllegalStateException("Step failed with exit code " + exitCode + ": " + line);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + log, e);
        }
    }

    /** Writes everything to two writers; closing it only flushes them, the caller closes its own writers. */
    static final class TeeWriter extends Writer {

        private final Writer first;
        private final Writer second;

        TeeWriter(Writer first, Writer second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            first.write(buffer, offset, length);
            second.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
