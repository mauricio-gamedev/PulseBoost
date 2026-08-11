package io.github.astromg01.pulseboost;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;

import java.util.Locale;

final class DeviceStats {
    private static final long GIB = 1024L * 1024L * 1024L;

    final long totalMemory;
    final long availableMemory;
    final boolean lowMemory;
    final float batteryTemperature;
    final int batteryLevel;
    final boolean charging;
    final long totalStorage;
    final long availableStorage;
    final boolean powerSaveMode;
    final int readinessScore;
    final String readinessTitle;
    final String recommendation;

    private DeviceStats(
            long totalMemory,
            long availableMemory,
            boolean lowMemory,
            float batteryTemperature,
            int batteryLevel,
            boolean charging,
            long totalStorage,
            long availableStorage,
            boolean powerSaveMode) {
        this.totalMemory = totalMemory;
        this.availableMemory = availableMemory;
        this.lowMemory = lowMemory;
        this.batteryTemperature = batteryTemperature;
        this.batteryLevel = batteryLevel;
        this.charging = charging;
        this.totalStorage = totalStorage;
        this.availableStorage = availableStorage;
        this.powerSaveMode = powerSaveMode;

        int score = 100;
        double memoryRatio = totalMemory > 0 ? (double) availableMemory / totalMemory : 0.0;
        double storageRatio = totalStorage > 0 ? (double) availableStorage / totalStorage : 0.0;

        if (batteryTemperature >= 43f) {
            score -= 42;
        } else if (batteryTemperature >= 40f) {
            score -= 24;
        } else if (batteryTemperature >= 38f) {
            score -= 10;
        }

        if (lowMemory || memoryRatio < 0.15) {
            score -= 28;
        } else if (memoryRatio < 0.25) {
            score -= 13;
        }

        if (availableStorage < 4L * GIB || storageRatio < 0.08) {
            score -= 22;
        } else if (availableStorage < 8L * GIB || storageRatio < 0.15) {
            score -= 9;
        }

        if (powerSaveMode) {
            score -= 14;
        }
        if (batteryLevel >= 0 && batteryLevel < 15 && !charging) {
            score -= 9;
        }

        readinessScore = Math.max(0, Math.min(100, score));
        if (readinessScore >= 85) {
            readinessTitle = "Pronto para jogar";
        } else if (readinessScore >= 65) {
            readinessTitle = "Dá para melhorar";
        } else {
            readinessTitle = "Risco de stutter alto";
        }

        if (batteryTemperature >= 43f) {
            recommendation = "O aparelho está quente. Espere esfriar antes de iniciar o jogo.";
        } else if (lowMemory || memoryRatio < 0.15) {
            recommendation = "Há pouca RAM livre. A limpeza de processos em cache pode ajudar agora.";
        } else if (availableStorage < 4L * GIB || storageRatio < 0.08) {
            recommendation = "Libere armazenamento: Android lento para gravar dados causa travadinhas.";
        } else if (powerSaveMode) {
            recommendation = "A economia de bateria está limitando desempenho e será desligada na sessão.";
        } else if (batteryTemperature >= 38f) {
            recommendation = "A temperatura já está subindo. Evite jogar carregando e reduza o brilho.";
        } else {
            recommendation = "Temperatura, memória e armazenamento estão em uma faixa boa.";
        }
    }

    static DeviceStats read(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        Intent battery = context.registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1;
        float temperature = 0f;
        boolean charging = false;
        if (battery != null) {
            int rawLevel = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = scale > 0 && rawLevel >= 0 ? Math.round(rawLevel * 100f / scale) : -1;
            temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            int status = battery.getIntExtra(
                    BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        return new DeviceStats(
                memoryInfo.totalMem,
                memoryInfo.availMem,
                memoryInfo.lowMemory,
                temperature,
                level,
                charging,
                statFs.getTotalBytes(),
                statFs.getAvailableBytes(),
                powerManager != null && powerManager.isPowerSaveMode());
    }

    static String formatBytes(long bytes) {
        if (bytes >= GIB) {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (double) GIB);
        }
        long mib = 1024L * 1024L;
        return String.format(Locale.getDefault(), "%.0f MB", bytes / (double) mib);
    }
}
