package ua.kpi.petri.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CsvWriter {

    private final List<String> lines = new ArrayList<>();

    public CsvWriter(String... header) {
        row((Object[]) header);
    }

    public CsvWriter row(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(format(values[i]));
        }
        lines.add(line.toString());
        return this;
    }

    public void write(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    private static String format(Object value) {
        String text = switch (value) {
            case null -> "";
            case Double _, Float _ -> String.format(Locale.ROOT, "%.6f", value);
            default -> value.toString();
        };
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
