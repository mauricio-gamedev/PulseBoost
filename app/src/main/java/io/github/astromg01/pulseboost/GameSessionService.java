package io.github.astromg01.pulseboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameSessionService extends Service {
    private static final String CHANNEL_ID = "pulseboost_game_session";
    private static final int NOTIFICATION_ID = 2206;
    private static final String ACTION_START = "io.github.astromg01.pulseboost.START_SESSION";
    private static final String ACTION_RESTORE = "io.github.astromg01.pulseboost.RESTORE_SESSION";

    private static final long SAMPLE_INTERVAL_MS = 15_000L;
    private static final long GAME_EXIT_GRACE_MS = 75_000L;
    private static final long RESTORE_RETRY_MS = 30_000L;

    static boolean start(Context context) {
        try {
            Intent intent = new Intent(context, GameSessionService.class)
                    .setAction(ACTION_START);
            context.startForegroundService(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void stopMonitoring(Context context) {
        try {
            context.stopService(new Intent(context, GameSessionService.class));
        } catch (Throwable ignored) {
            // O encerramento da sessão no Optimizer continua sendo a fonte de verdade.
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean restoring = new AtomicBoolean(false);

    private SharedPreferences preferences;
    private ShizukuController shizuku;
    private Optimizer optimizer;
    private NotificationManager notificationManager;
    private long lastGameSeenElapsed;
    private boolean gameSeen;
    private boolean restoreRequested;
    private boolean thermalWarning;
    private int hotSamples;
    private boolean thermalGuardRunning;
    private boolean thermalGuardAttempted;

    private final Runnable monitor = new Runnable() {
        @Override
        public void run() {
            monitorSession();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(Optimizer.PREFS, Context.MODE_PRIVATE);
        shizuku = ShizukuController.get(this);
        optimizer = new Optimizer(this, shizuku);
        notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!optimizer.hasActiveSession()) {
            stopNow();
            return START_NOT_STICKY;
        }

        promoteToForeground(buildNotification(null, false));
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_RESTORE.equals(action)) {
            requestRestore("Encerrada pela notificação");
        } else {
            scheduleMonitor(0L);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void monitorSession() {
        handler.removeCallbacks(monitor);
        if (!optimizer.hasActiveSession()) {
            stopNow();
            return;
        }
        if (restoreRequested) {
            attemptRestore();
            return;
        }

        DeviceStats stats = null;
        try {
            stats = DeviceStats.read(this);
            SessionHistory.sample(preferences, stats);
            thermalWarning = stats.batteryTemperature >= 42.5f;
            if (optimizer.isFrameSenseActive()) {
                SessionHistory.updateFramePlan(
                        preferences, FrameSense.decide(this, stats));
            }
            if (thermalWarning) {
                hotSamples++;
            } else if (stats.batteryTemperature < 41f) {
                hotSamples = 0;
            }
            maybeApplyThermalGuard();
        } catch (Throwable ignored) {
            // A sessão continua mesmo se uma leitura momentânea falhar.
        }

        boolean usageGranted = UsageAccess.isGranted(this);
        if (preferences.getBoolean(Optimizer.KEY_BACKGROUND_MONITOR, true) && usageGranted) {
            String targetPackage = optimizer.getSessionPackage();
            String foregroundPackage = UsageAccess.currentForegroundPackage(this);
            long now = SystemClock.elapsedRealtime();
            if (targetPackage != null && targetPackage.equals(foregroundPackage)) {
                gameSeen = true;
                lastGameSeenElapsed = now;
            } else if (gameSeen && now - lastGameSeenElapsed >= GAME_EXIT_GRACE_MS) {
                requestRestore("Jogo fechado • restauração automática");
                return;
            }
        }

        updateNotification(stats, false);
        scheduleMonitor(SAMPLE_INTERVAL_MS);
    }

    private void maybeApplyThermalGuard() {
        if (hotSamples < 2
                || thermalGuardRunning
                || thermalGuardAttempted
                || !optimizer.canApplyThermalGuard()
                || !shizuku.isReady()) {
            return;
        }
        thermalGuardAttempted = true;
        thermalGuardRunning = true;
        shizuku.runInBackground(() -> {
            ShellResult result = optimizer.applyThermalGuard();
            shizuku.postToMain(() -> {
                thermalGuardRunning = false;
                if (result.isSuccess()) {
                    updateNotification(null, false);
                }
            });
        });
    }

    private void requestRestore(String reason) {
        restoreRequested = true;
        SessionHistory.setEndReason(preferences, reason);
        attemptRestore();
    }

    private void attemptRestore() {
        if (!optimizer.hasActiveSession()) {
            stopNow();
            return;
        }
        if (!restoring.compareAndSet(false, true)) {
            return;
        }

        if (!shizuku.isReady()) {
            shizuku.refresh();
            restoring.set(false);
            updateNotification(null, true);
            scheduleMonitor(RESTORE_RETRY_MS);
            return;
        }

        updateNotification(null, true);
        shizuku.runInBackground(() -> {
            Optimizer.Report report = optimizer.restore();
            shizuku.postToMain(() -> {
                restoring.set(false);
                if (report.fullyRestored || !optimizer.hasActiveSession()) {
                    stopNow();
                } else {
                    updateNotification(null, true);
                    scheduleMonitor(RESTORE_RETRY_MS);
                }
            });
        });
    }

    private void scheduleMonitor(long delayMs) {
        handler.removeCallbacks(monitor);
        handler.postDelayed(monitor, delayMs);
    }

    private void createNotificationChannel() {
        if (notificationManager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(io.github.astromg01.pulseboost.R.string.session_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantém o perfil temporário e permite restaurá-lo durante o jogo.");
        channel.setShowBadge(false);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private void promoteToForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(DeviceStats stats, boolean restorePending) {
        if (notificationManager != null) {
            notificationManager.notify(
                    NOTIFICATION_ID, buildNotification(stats, restorePending));
        }
    }

    private Notification buildNotification(DeviceStats suppliedStats, boolean restorePending) {
        DeviceStats stats = suppliedStats;
        if (stats == null) {
            try {
                stats = DeviceStats.read(this);
            } catch (Throwable ignored) {
                // A notificação ainda pode ser criada sem telemetria.
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent restoreIntent = new Intent(this, GameSessionService.class)
                .setAction(ACTION_RESTORE);
        PendingIntent restorePendingIntent = PendingIntent.getService(
                this,
                11,
                restoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String label = optimizer.getSessionLabel();
        String profile = optimizer.getSessionProfile();
        String title = restorePending
                ? "Restauração pendente"
                : "PulseBoost ativo • " + (label == null ? "jogo" : label);
        String detail;
        if (restorePending) {
            detail = shizuku.isReady()
                    ? "Restaurando os ajustes temporários…"
                    : "Abra o Shizuku; o PulseBoost tentará novamente.";
        } else if (optimizer.wasThermalGuardApplied()) {
            detail = "FrameSense térmico ativo • modo Performance revertido com segurança";
            if (stats != null && stats.batteryTemperature > 0f) {
                detail += String.format(Locale.getDefault(),
                        " • %.1f °C • %s RAM",
                        stats.batteryTemperature,
                        DeviceStats.formatBytes(stats.availableMemory));
            }
        } else if (thermalWarning) {
            detail = "Temperatura alta • pause e deixe o A06 esfriar";
        } else {
            detail = profile == null ? "Perfil inteligente em execução" : profile;
            if (preferences.contains(SessionHistory.KEY_CURRENT_TARGET_FPS)) {
                detail += " • alvo "
                        + SessionHistory.currentTargetFps(preferences)
                        + " FPS";
            }
            if (stats != null && stats.batteryTemperature > 0f) {
                detail += String.format(Locale.getDefault(),
                        " • %.1f °C • %s RAM",
                        stats.batteryTemperature,
                        DeviceStats.formatBytes(stats.availableMemory));
            }
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(io.github.astromg01.pulseboost.R.drawable.ic_stat_pulse)
                .setColor(Color.rgb(138, 108, 255))
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setSubText(elapsedText())
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        io.github.astromg01.pulseboost.R.drawable.ic_stat_pulse,
                        "Restaurar",
                        restorePendingIntent).build());
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private String elapsedText() {
        long started = optimizer.getSessionStartedAt();
        long elapsed = started > 0 ? Math.max(0L, System.currentTimeMillis() - started) : 0L;
        long minutes = Math.max(1L, elapsed / 60_000L);
        return minutes == 1L ? "1 minuto de sessão" : minutes + " minutos de sessão";
    }

    private void stopNow() {
        handler.removeCallbacksAndMessages(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
}
