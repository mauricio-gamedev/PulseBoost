package io.github.astromg01.cloudkeys.overlay;

import android.content.Context;
import android.content.SharedPreferences;

// Per-package state is opt-in through edits; untouched games stay on the global defaults.\npublic final class ProfileStore {
    private static final String PREFS = "cloudkeys_profiles";

    private final SharedPreferences prefs;

    public ProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasProfile(String packageName) {
        return packageName != null
                && prefs.getBoolean(prefix(packageName) + "configured", false);
    }

    public void markConfigured(String packageName) {
        if (packageName == null) return;
        prefs.edit().putBoolean(prefix(packageName) + "configured", true).apply();
    }

    public int getX(String packageName, int index, int fallback) {
        return prefs.getInt(prefix(packageName) + "x_" + index, fallback);
    }

    public int getY(String packageName, int index, int fallback) {
        return prefs.getInt(prefix(packageName) + "y_" + index, fallback);
    }

    public void savePosition(String packageName, int index, int x, int y) {
        if (packageName == null) return;
        prefs.edit()
                .putBoolean(prefix(packageName) + "configured", true)
                .putInt(prefix(packageName) + "x_" + index, x)
                .putInt(prefix(packageName) + "y_" + index, y)
                .apply();
    }

    public float getOpacity(String packageName, float fallback) {
        return prefs.getFloat(prefix(packageName) + "opacity", fallback);
    }

    public float getScale(String packageName, float fallback) {
        return prefs.getFloat(prefix(packageName) + "scale", fallback);
    }

    public int getCursorX(String packageName, int fallback) {
        return prefs.getInt(prefix(packageName) + "cursor_x", fallback);
    }

    public int getCursorY(String packageName, int fallback) {
        return prefs.getInt(prefix(packageName) + "cursor_y", fallback);
    }

    public float getCursorSize(String packageName, float fallback) {
        return prefs.getFloat(prefix(packageName) + "cursor_size", fallback);
    }

    public float getCursorSpeed(String packageName, float fallback) {
        return prefs.getFloat(prefix(packageName) + "cursor_speed", fallback);
    }

    public float getCursorOpacity(String packageName, float fallback) {
        return prefs.getFloat(prefix(packageName) + "cursor_opacity", fallback);
    }

    public boolean getCursorEnabled(String packageName, boolean fallback) {
        return prefs.getBoolean(prefix(packageName) + "cursor_enabled", fallback);
    }

    public void saveAppearance(
            String packageName,
            float opacity,
            float scale
    ) {
        if (packageName == null) return;
        prefs.edit()
                .putBoolean(prefix(packageName) + "configured", true)
                .putFloat(prefix(packageName) + "opacity", opacity)
                .putFloat(prefix(packageName) + "scale", scale)
                .apply();
    }

    public void saveCursor(
            String packageName,
            int x,
            int y,
            float size,
            float speed,
            float opacity,
            boolean enabled
    ) {
        if (packageName == null) return;
        prefs.edit()
                .putBoolean(prefix(packageName) + "configured", true)
                .putInt(prefix(packageName) + "cursor_x", x)
                .putInt(prefix(packageName) + "cursor_y", y)
                .putFloat(prefix(packageName) + "cursor_size", size)
                .putFloat(prefix(packageName) + "cursor_speed", speed)
                .putFloat(prefix(packageName) + "cursor_opacity", opacity)
                .putBoolean(prefix(packageName) + "cursor_enabled", enabled)
                .apply();
    }

    private String prefix(String packageName) {
        String safe = packageName.replaceAll("[^A-Za-z0-9_]", "_");
        return "profile_" + safe + "_";
    }
}
