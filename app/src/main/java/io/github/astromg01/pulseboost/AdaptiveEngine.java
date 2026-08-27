package io.github.astromg01.pulseboost;

import java.util.Locale;

final class AdaptiveEngine {
    static final class Plan {
        final String title;
        final String detail;
        final String reportDetail;
        final boolean adaptive;
        final boolean trimCache;
        final boolean disableSaver;
        final String gameMode;
        final boolean doNotDisturb;
        final boolean animations;

        Plan(
                String title,
                String detail,
                String reportDetail,
                boolean adaptive,
                boolean trimCache,
                boolean disableSaver,
                String gameMode,
                boolean doNotDisturb,
                boolean animations) {
            this.title = title;
            this.detail = detail;
            this.reportDetail = reportDetail;
            this.adaptive = adaptive;
            this.trimCache = trimCache;
            this.disableSaver = disableSaver;
            this.gameMode = gameMode;
            this.doNotDisturb = doNotDisturb;
            this.animations = animations;
        }

        String summary() {
            return title + " • " + reportDetail;
        }
    }

    private AdaptiveEngine() {
    }

    static Plan decide(DeviceStats stats, Optimizer.Options options) {
        if (!options.adaptive) {
            OptimizationAdvisor.Explanation explanation = OptimizationAdvisor.explain(
                    stats,
                    options,
                    false,
                    options.trimCache,
                    options.disableSaver,
                    options.gameMode ? "performance" : null);
            return new Plan(
                    "Potência manual",
                    explanation.uiDetail,
                    explanation.reportDetail,
                    false,
                    options.trimCache,
                    options.disableSaver,
                    options.gameMode ? "performance" : null,
                    options.doNotDisturb,
                    options.animations);
        }

        double memoryRatio = stats.totalMemory > 0
                ? (double) stats.availableMemory / stats.totalMemory
                : 0.0;
        boolean memoryPressure = stats.lowMemory
                || memoryRatio < 0.34
                || stats.availableMemory < 1150L * 1024L * 1024L;
        boolean hot = stats.batteryTemperature >= 41.5f;
        boolean warm = stats.batteryTemperature >= 38.5f
                || (stats.charging && stats.batteryTemperature >= 37.5f);
        boolean lowBattery = stats.batteryLevel >= 0
                && stats.batteryLevel < 20
                && !stats.charging;

        String title;
        String detail;
        String gameMode = null;
        boolean disableSaver = options.disableSaver;

        if (hot) {
            title = "Proteção anti-throttle";
            detail = String.format(Locale.getDefault(),
                    "%.1f °C: priorizando estabilidade térmica", stats.batteryTemperature);
            gameMode = options.gameMode ? "standard" : null;
            disableSaver = false;
        } else if (warm) {
            title = "Estabilidade térmica";
            detail = String.format(Locale.getDefault(),
                    "%.1f °C: evitando aquecimento acelerado", stats.batteryTemperature);
            gameMode = options.gameMode ? "standard" : null;
        } else if (lowBattery) {
            title = "Equilíbrio energético";
            detail = stats.batteryLevel + "% de bateria: potência sem gasto desnecessário";
            gameMode = options.gameMode ? "standard" : null;
        } else if (memoryPressure) {
            title = "Recuperação de memória";
            detail = "Pouca RAM livre: limpeza seletiva antes do jogo";
            gameMode = options.gameMode ? "performance" : null;
        } else {
            title = "Desempenho inteligente";
            detail = "Temperatura e memória permitem o perfil mais forte";
            gameMode = options.gameMode ? "performance" : null;
        }

        boolean shouldTrim = options.trimCache && memoryPressure;
        OptimizationAdvisor.Explanation explanation = OptimizationAdvisor.explain(
                stats,
                options,
                true,
                shouldTrim,
                disableSaver,
                gameMode);
        return new Plan(
                title,
                explanation.uiDetail,
                explanation.reportDetail,
                true,
                shouldTrim,
                disableSaver,
                gameMode,
                options.doNotDisturb,
                options.animations);
    }
}
