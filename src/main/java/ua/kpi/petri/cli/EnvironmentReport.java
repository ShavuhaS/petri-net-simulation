package ua.kpi.petri.cli;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Conditions of the experiments: processor, memory, operating system and Java, as far as Java can tell. */
final class EnvironmentReport {

    private EnvironmentReport() {
    }

    static List<String> lines() {
        Runtime runtime = Runtime.getRuntime();
        List<String> lines = new ArrayList<>();
        lines.add("Date:              " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")));
        lines.add("Processor:         " + processorModel());
        lines.add("Logical CPUs:      " + runtime.availableProcessors());
        lines.add("Physical memory:   " + physicalMemory());
        lines.add("Operating system:  " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        lines.add("Java:              " + System.getProperty("java.vm.name") + " " + Runtime.version()
                + " (" + System.getProperty("java.vendor") + ")");
        lines.add("Maximum heap:      " + runtime.maxMemory() / (1024 * 1024) + " MiB");
        return lines;
    }

    private static String processorModel() {
        String windows = System.getenv("PROCESSOR_IDENTIFIER");
        if (windows != null && !windows.isBlank()) {
            return windows.strip();
        }
        try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("model name")) {
                    return line.substring(line.indexOf(':') + 1).strip();
                }
            }
        } catch (IOException | RuntimeException e) {
            return "unknown";
        }
        return "unknown";
    }

    private static String physicalMemory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getTotalMemorySize() / (1024 * 1024) + " MiB";
        }
        return "unknown";
    }
}
