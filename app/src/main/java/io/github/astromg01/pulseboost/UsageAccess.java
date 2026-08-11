package io.github.astromg01.pulseboost;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

final class UsageAccess {
    private static final long LOOKBACK_MS = 180_000L;

    private UsageAccess() {
    }

    static boolean isGranted(Context context) {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String currentForegroundPackage(Context context) {
        if (!isGranted(context)) {
            return null;
        }
        UsageStatsManager manager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return null;
        }

        long end = System.currentTimeMillis();
        UsageEvents events;
        try {
            events = manager.queryEvents(end - LOOKBACK_MS, end);
        } catch (Throwable ignored) {
            return null;
        }
        if (events == null) {
            return null;
        }

        UsageEvents.Event event = new UsageEvents.Event();
        String foregroundPackage = null;
        long newestTimestamp = Long.MIN_VALUE;
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            int type = event.getEventType();
            if (type != UsageEvents.Event.MOVE_TO_FOREGROUND
                    && type != UsageEvents.Event.ACTIVITY_RESUMED) {
                continue;
            }
            if (event.getTimeStamp() >= newestTimestamp) {
                newestTimestamp = event.getTimeStamp();
                foregroundPackage = event.getPackageName();
            }
        }
        return foregroundPackage;
    }
}
