package io.github.astromg01.cloudkeys.overlay;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import java.util.List;

public final class ForegroundDetector {
    public interface Listener {
        void onForegroundPackageChanged(String packageName);
    }

    private static final long POLL_MS = 1000L;
    private static final long EVENT_WINDOW_MS = 5000L;

    private final Context context;
    private final String ignoredPackage;
    private final Listener listener;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());
    private final HandlerThread workerThread =
            new HandlerThread(
                    "CloudKeysDetector",
                    Process.THREAD_PRIORITY_BACKGROUND
            );
    private Handler workerHandler;

    private boolean running;
    private boolean workerStarted;
    private String lastPackage;

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            String packageName = queryForegroundPackage();
            if (packageName != null
                    && !packageName.equals(lastPackage)) {
                lastPackage = packageName;
                mainHandler.post(
                        () -> listener.onForegroundPackageChanged(
                                packageName
                        )
                );
            }

            workerHandler.postDelayed(this, POLL_MS);
        }
    };

    public ForegroundDetector(
            Context context,
            String ignoredPackage,
            Listener listener
    ) {
        this.context = context.getApplicationContext();
        this.ignoredPackage = ignoredPackage;
        this.listener = listener;
    }

    public void start() {
        if (running) return;

        running = true;

        if (!workerStarted) {
            workerThread.start();
            workerStarted = true;
        }

        if (workerHandler == null) {
            workerHandler = new Handler(
                    workerThread.getLooper()
            );
        }
        workerHandler.post(poller);
    }

    public void stop() {
        running = false;

        if (workerHandler != null) {
            workerHandler.removeCallbacks(poller);
        }

    }

    public void shutdown() {
        running = false;

        if (workerHandler != null) {
            workerHandler.removeCallbacks(poller);
        }

        if (workerStarted && workerThread.isAlive()) {
            workerThread.quitSafely();
        }
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps =
                (AppOpsManager) context.getSystemService(
                        Context.APP_OPS_SERVICE
                );

        if (appOps == null) return false;

        try {
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    context.getApplicationInfo().uid,
                    context.getPackageName()
            );
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String queryForegroundPackage() {
        if (!hasUsageAccess(context)) return null;

        UsageStatsManager manager =
                (UsageStatsManager) context.getSystemService(
                        Context.USAGE_STATS_SERVICE
                );
        if (manager == null) return null;

        long now = System.currentTimeMillis();
        long begin = now - EVENT_WINDOW_MS;

        if (Build.VERSION.SDK_INT >= 21) {
            String fromEvents =
                    queryEvents(manager, begin, now);
            if (fromEvents != null) return fromEvents;
        }

        return queryStats(manager, begin, now);
    }

    private String queryEvents(
            UsageStatsManager manager,
            long begin,
            long end
    ) {
        UsageEvents events =
                manager.queryEvents(begin, end);
        if (events == null) return null;

        UsageEvents.Event event =
                new UsageEvents.Event();

        String newest = null;
        long newestTime = -1L;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);

            int type = event.getEventType();
            boolean foreground;

            if (Build.VERSION.SDK_INT >= 29) {
                foreground =
                        type == UsageEvents.Event.ACTIVITY_RESUMED
                                || type
                                == UsageEvents.Event.MOVE_TO_FOREGROUND;
            } else {
                foreground =
                        type
                                == UsageEvents.Event.MOVE_TO_FOREGROUND;
            }

            if (!foreground
                    || event.getTimeStamp() < newestTime) {
                continue;
            }

            String packageName = event.getPackageName();
            if (isEligiblePackage(packageName)) {
                newest = packageName;
                newestTime = event.getTimeStamp();
            }
        }

        return newest;
    }

    private String queryStats(
            UsageStatsManager manager,
            long begin,
            long end
    ) {
        List<UsageStats> stats =
                manager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        begin,
                        end
                );

        if (stats == null || stats.isEmpty()) {
            return null;
        }

        String newest = null;
        long newestTime = -1L;

        for (UsageStats stat : stats) {
            String packageName = stat.getPackageName();
            if (!isEligiblePackage(packageName)) {
                continue;
            }

            long used = stat.getLastTimeUsed();
            if (used < begin) continue;

            if (used > newestTime) {
                newestTime = used;
                newest = packageName;
            }
        }

        return newest;
    }

    private boolean isEligiblePackage(String packageName) {
        if (packageName == null
                || packageName.equals(ignoredPackage)) {
            return false;
        }

        try {
            ApplicationInfo info =
                    context.getPackageManager()
                            .getApplicationInfo(
                                    packageName,
                                    0
                            );

            // Ignore Android/system packages. Any normal third-party
            // game or app can become the active CloudKeys profile.
            return (info.flags
                    & ApplicationInfo.FLAG_SYSTEM) == 0
                    && (info.flags
                    & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
