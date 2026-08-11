package io.github.astromg01.pulseboost;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import java.util.Locale;

final class FrameSense {
    static final class Plan {
        final float displayRefreshRate;
        final int targetFps;
        final double frameBudgetMs;
        final String title;
        final String detail;
        final String qualityHint;

        Plan(
                float displayRefreshRate,
                int targetFps,
                String title,
                String detail,
                String qualityHint) {
            this.displayRefreshRate = displayRefreshRate;
            this.targetFps = targetFps;
            this.frameBudgetMs = targetFps > 0 ? 1000d / targetFps : 16.67d;
            this.title = title;
            this.detail = detail;
            this.qualityHint = qualityHint;
        }

        String targetLabel() {
            return targetFps + " FPS recomendado";
        }

        String displayLabel() {
            return String.format(Locale.getDefault(), "%.0f Hz", displayRefreshRate);
        }

        String budgetLabel() {
            return String.format(Locale.getDefault(), "%.1f ms/quadro", frameBudgetMs);
        }
    }

    private FrameSense() {
    }

    static Plan decide(Context context, DeviceStats stats) {
        float refreshRate = readRefreshRate(context);
        int maximumTarget = nearestSupportedMaximum(refreshRate);
        int stableTarget = stableDivisor(maximumTarget);
        double memoryRatio = stats.totalMemory > 0
                ? (double) stats.availableMemory / stats.totalMemory
                : 0.0;

        boolean criticalHeat = stats.batteryTemperature >= 41.5f;
        boolean warm = stats.batteryTemperature >= 38.5f
                || (stats.charging && stats.batteryTemperature >= 37.5f);
        boolean lowBattery = stats.batteryLevel >= 0
                && stats.batteryLevel < 20
                && !stats.charging;
        boolean memoryPressure = stats.lowMemory
                || memoryRatio < 0.28
                || stats.availableMemory < 900L * 1024L * 1024L;

        int target = maximumTarget;
        String title;
        String detail;
        String quality;
        if (criticalHeat) {
            target = stableTarget;
            title = "Estabilidade antes de picos";
            detail = "Calor alto: um alvo menor e constante tende a travar menos";
            quality = "Use gráficos baixos e evite jogar carregando";
        } else if (warm || lowBattery) {
            target = stableTarget;
            title = "Frame pacing equilibrado";
            detail = warm
                    ? "Temperatura subindo: priorize constância em vez do maior número"
                    : "Bateria baixa: alvo estável reduz oscilações de energia";
            quality = "Prefira gráficos baixos ou médios";
        } else if (memoryPressure) {
            title = "FPS com memória protegida";
            detail = "O alvo pode ser alto, mas texturas pesadas aumentam recarregamentos";
            quality = "Reduza texturas e distância de renderização";
        } else {
            title = "Maior alvo sustentável";
            detail = "Tela, temperatura e memória estão em uma faixa favorável";
            quality = "Comece no baixo/médio e aumente aos poucos";
        }

        return new Plan(refreshRate, target, title, detail, quality);
    }

    private static float readRefreshRate(Context context) {
        try {
            DisplayManager manager =
                    (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = manager == null
                    ? null
                    : manager.getDisplay(Display.DEFAULT_DISPLAY);
            float refresh = display == null ? 0f : display.getMode().getRefreshRate();
            return refresh >= 24f && refresh <= 240f ? refresh : 60f;
        } catch (Throwable ignored) {
            return 60f;
        }
    }

    private static int nearestSupportedMaximum(float refreshRate) {
        if (refreshRate >= 105f) {
            return 120;
        }
        if (refreshRate >= 75f) {
            return 90;
        }
        return 60;
    }

    private static int stableDivisor(int maximumTarget) {
        if (maximumTarget >= 120) {
            return 60;
        }
        if (maximumTarget >= 90) {
            return 45;
        }
        return 30;
    }
}
