package io.github.astromg01.pulseboost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class FrameStatsAnalyzer {
    private static final String MARKER = "---PROFILEDATA---";

    static final class Result {
        final boolean available;
        final int totalFrames;
        final int unstableFrames;
        final int stabilityPercent;
        final double medianMs;
        final double percentile95Ms;

        Result(
                boolean available,
                int totalFrames,
                int unstableFrames,
                int stabilityPercent,
                double medianMs,
                double percentile95Ms) {
            this.available = available;
            this.totalFrames = totalFrames;
            this.unstableFrames = unstableFrames;
            this.stabilityPercent = stabilityPercent;
            this.medianMs = medianMs;
            this.percentile95Ms = percentile95Ms;
        }

        String rating() {
            if (!available) {
                return "Sem amostra compatível";
            }
            if (stabilityPercent >= 90) {
                return "Frames estáveis";
            }
            if (stabilityPercent >= 75) {
                return "Oscilações moderadas";
            }
            return "Stutter detectado";
        }

        String summary() {
            if (!available) {
                return "O jogo não expôs dados recentes de frame ao Android";
            }
            return String.format(Locale.getDefault(),
                    "%d%% estáveis • mediana %.1f ms • p95 %.1f ms • %d quadros analisados",
                    stabilityPercent,
                    medianMs,
                    percentile95Ms,
                    totalFrames);
        }
    }

    private FrameStatsAnalyzer() {
    }

    static Result parse(String output, int targetFps) {
        if (output == null || output.trim().isEmpty()) {
            return unavailable();
        }

        List<Double> durations = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int start = output.indexOf(MARKER, cursor);
            if (start < 0) {
                break;
            }
            int end = output.indexOf(MARKER, start + MARKER.length());
            if (end < 0) {
                break;
            }
            parseBlock(output.substring(start + MARKER.length(), end), durations);
            cursor = end + MARKER.length();
        }

        if (durations.isEmpty()) {
            return unavailable();
        }
        Collections.sort(durations);
        double targetBudget = 1000d / clampTargetFps(targetFps);
        double unstableThreshold = targetBudget * 1.10d;
        int unstable = 0;
        for (double duration : durations) {
            if (duration > unstableThreshold) {
                unstable++;
            }
        }

        int total = durations.size();
        double median = percentile(durations, 0.50d);
        double p95 = percentile(durations, 0.95d);
        int stability = Math.max(0, Math.min(100,
                (int) Math.round((total - unstable) * 100d / total)));
        return new Result(true, total, unstable, stability, median, p95);
    }

    private static void parseBlock(String block, List<Double> durations) {
        String[] lines = block.split("\\r?\\n");
        int flagsIndex = -1;
        int intendedIndex = -1;
        int completedIndex = -1;
        boolean headerFound = false;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",");
            if (!headerFound) {
                for (int i = 0; i < parts.length; i++) {
                    String header = parts[i].trim();
                    if ("Flags".equals(header)) {
                        flagsIndex = i;
                    } else if ("IntendedVsync".equals(header)) {
                        intendedIndex = i;
                    } else if ("FrameCompleted".equals(header)) {
                        completedIndex = i;
                    }
                }
                headerFound = flagsIndex >= 0 && intendedIndex >= 0 && completedIndex >= 0;
                continue;
            }

            int required = Math.max(flagsIndex, Math.max(intendedIndex, completedIndex));
            if (parts.length <= required) {
                continue;
            }
            try {
                long flags = Long.parseLong(parts[flagsIndex].trim());
                long intended = Long.parseLong(parts[intendedIndex].trim());
                long completed = Long.parseLong(parts[completedIndex].trim());
                long durationNs = completed - intended;
                if (flags == 0L && intended > 0L
                        && durationNs > 0L && durationNs < 5_000_000_000L) {
                    durations.add(durationNs / 1_000_000d);
                }
            } catch (NumberFormatException ignored) {
                // Linhas incompletas ou específicas do fabricante são ignoradas.
            }
        }
    }

    private static double percentile(List<Double> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private static double clampTargetFps(int targetFps) {
        return targetFps >= 24 && targetFps <= 240 ? targetFps : 60d;
    }

    private static Result unavailable() {
        return new Result(false, 0, 0, 0, 0d, 0d);
    }
}
