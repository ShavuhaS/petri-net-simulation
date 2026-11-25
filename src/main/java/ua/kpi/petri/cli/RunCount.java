package ua.kpi.petri.cli;

import picocli.CommandLine.ITypeConverter;

/** Value of a {@code --runs} option: a positive number or {@code auto}. */
record RunCount(boolean auto, int count) {

    static final class Converter implements ITypeConverter<RunCount> {
        @Override
        public RunCount convert(String value) {
            if (value.equalsIgnoreCase("auto")) {
                return new RunCount(true, 0);
            }
            int count;
            try {
                count = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("expected a positive number or 'auto', got '" + value + "'");
            }
            if (count < 1) {
                throw new IllegalArgumentException("number of runs must be positive, got " + count);
            }
            return new RunCount(false, count);
        }
    }
}
