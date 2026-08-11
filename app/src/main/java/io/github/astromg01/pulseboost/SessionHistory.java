package io.github.astromg01.pulseboost;

import android.content.SharedPreferences;

import java.util.Locale;

final class SessionHistory {
    static final String KEY_CURRENT_PEAK_TEMP = "current_peak_temperature";
    static final String KEY_CURRENT_MIN_RAM = "current_min_free_ram";
    static final String KEY_SESSION_PROFILE = "session_profile";
    static final String KEY_SESSION_END_REASON = "session_end_reason";
    static final String KEY_CURRENT_TARGET_FPS = "current_target_fps";
    static final String KEY_CURRENT_DISPLAY_HZ = "current_display_hz";
    static final String KEY_CURRENT_FRAME_TOTAL = "current_frame_total";
    static final String KEY_CURRENT_FRAME_UNSTABLE = "current_frame_unstable";
    static final String KEY_CURRENT_FRAME_STABILITY = "current_frame_stability";
    static final String KEY_CURRENT_FRAME_MEDIAN = "current_frame_median";
    static final String KEY_CURRENT_FRAME_P95 = "current_frame_p95";

    static final String KEY_LAST_DURATION = "last_session_duration";
    static final String KEY_LAST_PEAK_TEMP = "last_peak_temperature";
    static final String KEY_LAST_MIN_RAM = "last_min_free_ram";
    static final String KEY_LAST_PROFILE = "last_session_profile";
    static final String KEY_LAST_REASON = "last_session_reason";
    static final String KEY_LAST_FINISHED = "last_session_finished";
    static final String KEY_LAST_TARGET_FPS = "last_target_fps";
    static final String KEY_LAST_DISPLAY_HZ = "last_display_hz";
    static final String KEY_LAST_FRAME_TOTAL = "last_frame_total";
    static final String KEY_LAST_FRAME_UNSTABLE = "last_frame_unstable";
    static final String KEY_LAST_FRAME_STABILITY = "last_frame_stability";
    static final String KEY_LAST_FRAME_MEDIAN = "last_frame_median";
    static final String KEY_LAST_FRAME_P95 = "last_frame_p95";

    private SessionHistory() {
    }

    static void begin(
            SharedPreferences preferences,
            DeviceStats stats,
            String profile,
            FrameSense.Plan framePlan) {
        SharedPreferences.Editor editor = preferences.edit()
                .putFloat(KEY_CURRENT_PEAK_TEMP, stats.batteryTemperature)
                .putLong(KEY_CURRENT_MIN_RAM, stats.availableMemory)
                .putString(KEY_SESSION_PROFILE, profile)
                .putString(KEY_SESSION_END_REASON, "Sessão encerrada")
                .remove(KEY_CURRENT_TARGET_FPS)
                .remove(KEY_CURRENT_DISPLAY_HZ)
                .remove(KEY_CURRENT_FRAME_TOTAL)
                .remove(KEY_CURRENT_FRAME_UNSTABLE)
                .remove(KEY_CURRENT_FRAME_STABILITY)
                .remove(KEY_CURRENT_FRAME_MEDIAN)
                .remove(KEY_CURRENT_FRAME_P95);
        if (framePlan != null) {
            editor.putInt(KEY_CURRENT_TARGET_FPS, framePlan.targetFps)
                    .putFloat(KEY_CURRENT_DISPLAY_HZ, framePlan.displayRefreshRate);
        }
        editor.apply();
    }

    static void sample(SharedPreferences preferences, DeviceStats stats) {
        float previousPeak = preferences.getFloat(
                KEY_CURRENT_PEAK_TEMP, stats.batteryTemperature);
        long previousMinimum = preferences.getLong(
                KEY_CURRENT_MIN_RAM, stats.availableMemory);
        SharedPreferences.Editor editor = preferences.edit();
        if (stats.batteryTemperature > previousPeak) {
            editor.putFloat(KEY_CURRENT_PEAK_TEMP, stats.batteryTemperature);
        }
        if (stats.availableMemory > 0
                && (previousMinimum <= 0 || stats.availableMemory < previousMinimum)) {
            editor.putLong(KEY_CURRENT_MIN_RAM, stats.availableMemory);
        }
        editor.apply();
    }

    static void setEndReason(SharedPreferences preferences, String reason) {
        if (reason != null && !reason.trim().isEmpty()) {
            preferences.edit().putString(KEY_SESSION_END_REASON, reason.trim()).apply();
        }
    }

    static int currentTargetFps(SharedPreferences preferences) {
        return preferences.getInt(KEY_CURRENT_TARGET_FPS, 60);
    }

    static float currentDisplayHz(SharedPreferences preferences) {
        return preferences.getFloat(KEY_CURRENT_DISPLAY_HZ, 60f);
    }

    static void updateFramePlan(
            SharedPreferences preferences,
            FrameSense.Plan framePlan) {
        if (framePlan == null) {
            return;
        }
        preferences.edit()
                .putInt(KEY_CURRENT_TARGET_FPS, framePlan.targetFps)
                .putFloat(KEY_CURRENT_DISPLAY_HZ, framePlan.displayRefreshRate)
                .apply();
    }

    static void storeFrameResult(
            SharedPreferences preferences,
            FrameStatsAnalyzer.Result result) {
        if (result == null || !result.available) {
            return;
        }
        preferences.edit()
                .putInt(KEY_CURRENT_FRAME_TOTAL, result.totalFrames)
                .putInt(KEY_CURRENT_FRAME_UNSTABLE, result.unstableFrames)
                .putInt(KEY_CURRENT_FRAME_STABILITY, result.stabilityPercent)
                .putFloat(KEY_CURRENT_FRAME_MEDIAN, (float) result.medianMs)
                .putFloat(KEY_CURRENT_FRAME_P95, (float) result.percentile95Ms)
                .apply();
    }

    static void complete(SharedPreferences preferences, long startedAt) {
        long now = System.currentTimeMillis();
        long duration = startedAt > 0 ? Math.max(0L, now - startedAt) : 0L;
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(KEY_LAST_DURATION, duration)
                .putFloat(KEY_LAST_PEAK_TEMP,
                        preferences.getFloat(KEY_CURRENT_PEAK_TEMP, 0f))
                .putLong(KEY_LAST_MIN_RAM,
                        preferences.getLong(KEY_CURRENT_MIN_RAM, 0L))
                .putString(KEY_LAST_PROFILE,
                        preferences.getString(KEY_SESSION_PROFILE, "Perfil inteligente"))
                .putString(KEY_LAST_REASON,
                        preferences.getString(KEY_SESSION_END_REASON, "Sessão encerrada"))
                .putLong(KEY_LAST_FINISHED, now)
                .remove(KEY_LAST_TARGET_FPS)
                .remove(KEY_LAST_DISPLAY_HZ)
                .remove(KEY_LAST_FRAME_TOTAL)
                .remove(KEY_LAST_FRAME_UNSTABLE)
                .remove(KEY_LAST_FRAME_STABILITY)
                .remove(KEY_LAST_FRAME_MEDIAN)
                .remove(KEY_LAST_FRAME_P95);
        if (preferences.contains(KEY_CURRENT_TARGET_FPS)) {
            editor.putInt(KEY_LAST_TARGET_FPS,
                    preferences.getInt(KEY_CURRENT_TARGET_FPS, 60));
        }
        if (preferences.contains(KEY_CURRENT_DISPLAY_HZ)) {
            editor.putFloat(KEY_LAST_DISPLAY_HZ,
                    preferences.getFloat(KEY_CURRENT_DISPLAY_HZ, 60f));
        }
        if (preferences.contains(KEY_CURRENT_FRAME_TOTAL)) {
            editor.putInt(KEY_LAST_FRAME_TOTAL,
                            preferences.getInt(KEY_CURRENT_FRAME_TOTAL, 0))
                    .putInt(KEY_LAST_FRAME_UNSTABLE,
                            preferences.getInt(KEY_CURRENT_FRAME_UNSTABLE, 0))
                    .putInt(KEY_LAST_FRAME_STABILITY,
                            preferences.getInt(KEY_CURRENT_FRAME_STABILITY, 0))
                    .putFloat(KEY_LAST_FRAME_MEDIAN,
                            preferences.getFloat(KEY_CURRENT_FRAME_MEDIAN, 0f))
                    .putFloat(KEY_LAST_FRAME_P95,
                            preferences.getFloat(KEY_CURRENT_FRAME_P95, 0f));
        }
        editor
                .remove(KEY_CURRENT_PEAK_TEMP)
                .remove(KEY_CURRENT_MIN_RAM)
                .remove(KEY_SESSION_PROFILE)
                .remove(KEY_SESSION_END_REASON)
                .remove(KEY_CURRENT_TARGET_FPS)
                .remove(KEY_CURRENT_DISPLAY_HZ)
                .remove(KEY_CURRENT_FRAME_TOTAL)
                .remove(KEY_CURRENT_FRAME_UNSTABLE)
                .remove(KEY_CURRENT_FRAME_STABILITY)
                .remove(KEY_CURRENT_FRAME_MEDIAN)
                .remove(KEY_CURRENT_FRAME_P95)
                .apply();
    }

    static boolean hasHistory(SharedPreferences preferences) {
        return preferences.contains(KEY_LAST_FINISHED);
    }

    static String title(SharedPreferences preferences) {
        return preferences.getString(KEY_LAST_PROFILE, "Última sessão");
    }

    static String detail(SharedPreferences preferences) {
        long duration = preferences.getLong(KEY_LAST_DURATION, 0L);
        float peak = preferences.getFloat(KEY_LAST_PEAK_TEMP, 0f);
        long minimumRam = preferences.getLong(KEY_LAST_MIN_RAM, 0L);
        String reason = preferences.getString(KEY_LAST_REASON, "Sessão encerrada");
        long minutes = Math.max(1L, Math.round(duration / 60_000d));

        StringBuilder text = new StringBuilder();
        text.append(minutes).append(" min");
        if (peak > 0f) {
            text.append(" • pico ")
                    .append(String.format(Locale.getDefault(), "%.1f °C", peak));
        }
        if (minimumRam > 0L) {
            text.append(" • RAM mínima ").append(DeviceStats.formatBytes(minimumRam));
        }
        if (preferences.contains(KEY_LAST_TARGET_FPS)) {
            int target = preferences.getInt(KEY_LAST_TARGET_FPS, 60);
            float display = preferences.getFloat(KEY_LAST_DISPLAY_HZ, 60f);
            text.append("\nFrameSense: alvo ")
                    .append(target)
                    .append(" FPS • tela ")
                    .append(String.format(Locale.getDefault(), "%.0f Hz", display));
        }
        int frameTotal = preferences.getInt(KEY_LAST_FRAME_TOTAL, 0);
        if (frameTotal > 0) {
            int stability = preferences.getInt(KEY_LAST_FRAME_STABILITY, 0);
            float median = preferences.getFloat(KEY_LAST_FRAME_MEDIAN, 0f);
            float p95 = preferences.getFloat(KEY_LAST_FRAME_P95, 0f);
            text.append("\n")
                    .append(stability)
                    .append("% estáveis • mediana ")
                    .append(String.format(Locale.getDefault(), "%.1f ms", median))
                    .append(" • p95 ")
                    .append(String.format(Locale.getDefault(), "%.1f ms", p95))
                    .append(" • ")
                    .append(frameTotal)
                    .append(" quadros");
        }
        text.append('\n').append(reason);
        return text.toString();
    }
}
