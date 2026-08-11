package io.github.astromg01.pulseboost;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Optimizer {
    static final String PREFS = "pulseboost";
    static final String KEY_SELECTED_PACKAGE = "selected_package";
    static final String KEY_SELECTED_LABEL = "selected_label";
    static final String KEY_TRIM_CACHE = "option_trim_cache";
    static final String KEY_DISABLE_SAVER = "option_disable_saver";
    static final String KEY_GAME_MODE = "option_game_mode";
    static final String KEY_DND = "option_dnd";
    static final String KEY_ANIMATIONS = "option_animations";
    static final String KEY_ADAPTIVE = "option_adaptive";
    static final String KEY_FRAME_SENSE = "option_frame_sense";
    static final String KEY_BACKGROUND_MONITOR = "option_background_monitor";
    static final String KEY_BACKGROUND_SERVICE = "option_background_service";

    private static final String KEY_SESSION_ACTIVE = "session_active";
    private static final String KEY_SESSION_PACKAGE = "session_package";
    private static final String KEY_SESSION_LABEL = "session_label";
    private static final String KEY_SESSION_STARTED = "session_started";
    private static final String KEY_PREV_LOW_POWER = "previous_low_power";
    private static final String KEY_PREV_GAME_MODE = "previous_game_mode";
    private static final String KEY_PREV_WINDOW_ANIM = "previous_window_animation";
    private static final String KEY_PREV_TRANSITION_ANIM = "previous_transition_animation";
    private static final String KEY_PREV_ANIMATOR = "previous_animator_duration";
    private static final String KEY_DND_APPLIED = "dnd_applied";
    private static final String KEY_PREV_DND = "previous_dnd";
    private static final String KEY_SESSION_FRAME_SENSE_ACTIVE = "session_frame_sense_active";
    private static final String KEY_SESSION_GAME_MODE_CONTROLLED = "session_game_mode_controlled";
    private static final String KEY_SESSION_CURRENT_GAME_MODE = "session_current_game_mode";
    private static final String KEY_THERMAL_GUARD_APPLIED = "thermal_guard_applied";
    private static final String KEY_FRAME_ANALYSIS_CAPTURED = "frame_analysis_captured";

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$");
    private static final Pattern GAME_MODE_PATTERN = Pattern.compile(
            "current mode:\\s*([a-z0-9-]+).*available game modes:\\s*\\[([^]]*)]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SETTING_VALUE_PATTERN =
            Pattern.compile("^(?:null|-?[0-9]+(?:\\.[0-9]+)?)$");

    static final class Options {
        final boolean trimCache;
        final boolean disableSaver;
        final boolean gameMode;
        final boolean doNotDisturb;
        final boolean animations;
        final boolean adaptive;
        final boolean frameSense;
        final boolean backgroundMonitor;
        final boolean backgroundService;

        Options(
                boolean trimCache,
                boolean disableSaver,
                boolean gameMode,
                boolean doNotDisturb,
                boolean animations,
                boolean adaptive,
                boolean frameSense,
                boolean backgroundMonitor,
                boolean backgroundService) {
            this.trimCache = trimCache;
            this.disableSaver = disableSaver;
            this.gameMode = gameMode;
            this.doNotDisturb = doNotDisturb;
            this.animations = animations;
            this.adaptive = adaptive;
            this.frameSense = frameSense;
            this.backgroundMonitor = backgroundMonitor;
            this.backgroundService = backgroundService;
        }

        static Options load(SharedPreferences preferences) {
            return new Options(
                    preferences.getBoolean(KEY_TRIM_CACHE, true),
                    preferences.getBoolean(KEY_DISABLE_SAVER, true),
                    preferences.getBoolean(KEY_GAME_MODE, true),
                    preferences.getBoolean(KEY_DND, true),
                    preferences.getBoolean(KEY_ANIMATIONS, false),
                    preferences.getBoolean(KEY_ADAPTIVE, true),
                    preferences.getBoolean(KEY_FRAME_SENSE, true),
                    preferences.getBoolean(KEY_BACKGROUND_MONITOR, true),
                    preferences.getBoolean(KEY_BACKGROUND_SERVICE, true));
        }
    }

    static final class ReportEntry {
        final String title;
        final String detail;
        final boolean success;
        final boolean skipped;

        ReportEntry(String title, String detail, boolean success, boolean skipped) {
            this.title = title;
            this.detail = detail;
            this.success = success;
            this.skipped = skipped;
        }
    }

    static final class Report {
        final String heading;
        final List<ReportEntry> entries = new ArrayList<>();
        boolean fullyRestored = true;

        Report(String heading) {
            this.heading = heading;
        }

        void success(String title, String detail) {
            entries.add(new ReportEntry(title, detail, true, false));
        }

        void skip(String title, String detail) {
            entries.add(new ReportEntry(title, detail, false, true));
        }

        void fail(String title, String detail) {
            entries.add(new ReportEntry(title, detail, false, false));
        }

        int successCount() {
            int count = 0;
            for (ReportEntry entry : entries) {
                if (entry.success) {
                    count++;
                }
            }
            return count;
        }
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final ShizukuController shizuku;

    Optimizer(Context context, ShizukuController shizuku) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.shizuku = shizuku;
    }

    boolean hasActiveSession() {
        return preferences.getBoolean(KEY_SESSION_ACTIVE, false);
    }

    String getSessionPackage() {
        return preferences.getString(KEY_SESSION_PACKAGE, null);
    }

    String getSessionLabel() {
        return preferences.getString(KEY_SESSION_LABEL, null);
    }

    String getSessionProfile() {
        return preferences.getString(SessionHistory.KEY_SESSION_PROFILE, null);
    }

    long getSessionStartedAt() {
        return preferences.getLong(KEY_SESSION_STARTED, 0L);
    }

    void setSessionEndReason(String reason) {
        SessionHistory.setEndReason(preferences, reason);
    }

    Report optimize(
            String packageName,
            String gameLabel,
            Options options,
            DeviceStats stats) {
        Report report = new Report("Relatório da otimização");
        if (!isSafePackage(packageName)) {
            report.fail("Jogo", "Pacote inválido; nenhuma alteração foi feita.");
            return report;
        }

        AdaptiveEngine.Plan plan = AdaptiveEngine.decide(stats, options);
        FrameSense.Plan framePlan = options.frameSense
                ? FrameSense.decide(context, stats)
                : null;
        long startedAt = System.currentTimeMillis();

        SharedPreferences.Editor session = preferences.edit()
                .putBoolean(KEY_SESSION_ACTIVE, true)
                .putString(KEY_SESSION_PACKAGE, packageName)
                .putString(KEY_SESSION_LABEL,
                        gameLabel == null || gameLabel.trim().isEmpty() ? packageName : gameLabel)
                .putLong(KEY_SESSION_STARTED, startedAt)
                .remove(KEY_PREV_LOW_POWER)
                .remove(KEY_PREV_GAME_MODE)
                .remove(KEY_PREV_WINDOW_ANIM)
                .remove(KEY_PREV_TRANSITION_ANIM)
                .remove(KEY_PREV_ANIMATOR)
                .putBoolean(KEY_DND_APPLIED, false)
                .remove(KEY_PREV_DND)
                .putBoolean(KEY_SESSION_FRAME_SENSE_ACTIVE, options.frameSense)
                .putBoolean(KEY_SESSION_GAME_MODE_CONTROLLED, false)
                .remove(KEY_SESSION_CURRENT_GAME_MODE)
                .putBoolean(KEY_THERMAL_GUARD_APPLIED, false)
                .putBoolean(KEY_FRAME_ANALYSIS_CAPTURED, false);
        session.apply();
        SessionHistory.begin(preferences, stats, plan.title, framePlan);
        report.success(plan.adaptive ? "Motor inteligente" : "Perfil manual", plan.summary());
        if (framePlan != null) {
            report.success("FrameSense",
                    framePlan.targetLabel() + " • " + framePlan.detail
                            + " (recomendação; não altera o FPS do jogo)");
        }

        if (plan.disableSaver) {
            String previous = readSetting("settings get global low_power");
            if (isSafeSettingValue(previous)) {
                preferences.edit().putString(KEY_PREV_LOW_POWER, previous).apply();
            }
            ShellResult result = shizuku.executeBlocking("settings put global low_power 0");
            addCommandResult(report, "Economia de bateria", result,
                    "Desligada durante esta sessão");
        } else if (options.disableSaver && plan.adaptive) {
            report.skip("Economia de bateria",
                    "Mantida pelo perfil inteligente para controlar calor ou consumo");
        }

        if (plan.gameMode != null) {
            applyGameMode(packageName, plan.gameMode, report);
        }

        if (plan.animations) {
            applyAnimations(report);
        }

        if (plan.trimCache) {
            ShellResult result = shizuku.executeBlocking("am kill-all");
            addCommandResult(report, "Processos em cache", result,
                    "Android liberou processos armazenados em segundo plano");
        } else if (options.trimCache && plan.adaptive) {
            report.skip("Processos em cache",
                    "RAM suficiente; limpeza ignorada para evitar recarregamentos desnecessários");
        }

        if (plan.doNotDisturb) {
            applyDoNotDisturb(report);
        }

        return report;
    }

    Report restore() {
        Report report = new Report("Restauração da sessão");
        if (!hasActiveSession()) {
            report.skip("Sessão", "Não há ajustes temporários para restaurar.");
            return report;
        }

        String packageName = preferences.getString(KEY_SESSION_PACKAGE, "");
        captureFrameStats(packageName, report);
        boolean shellRestoreNeeded = false;
        boolean shellRestoreFailed = false;

        String lowPower = preferences.getString(KEY_PREV_LOW_POWER, null);
        if (isSafeSettingValue(lowPower)) {
            shellRestoreNeeded = true;
            ShellResult result = shizuku.executeBlocking(
                    restoreSettingCommand("low_power", lowPower));
            if (result.isSuccess()) {
                report.success("Economia de bateria", "Estado anterior restaurado");
            } else {
                shellRestoreFailed = true;
                report.fail("Economia de bateria", usefulError(result));
            }
        }

        shellRestoreFailed |= !restoreAnimation(
                KEY_PREV_WINDOW_ANIM, "window_animation_scale", "Animação de janelas", report);
        shellRestoreFailed |= !restoreAnimation(
                KEY_PREV_TRANSITION_ANIM, "transition_animation_scale", "Transições", report);
        shellRestoreFailed |= !restoreAnimation(
                KEY_PREV_ANIMATOR, "animator_duration_scale", "Duração de animações", report);
        shellRestoreNeeded |= preferences.contains(KEY_PREV_WINDOW_ANIM)
                || preferences.contains(KEY_PREV_TRANSITION_ANIM)
                || preferences.contains(KEY_PREV_ANIMATOR);

        String previousGameMode = preferences.getString(KEY_PREV_GAME_MODE, null);
        boolean gameModeControlled = preferences.getBoolean(
                KEY_SESSION_GAME_MODE_CONTROLLED, false);
        if (isSafePackage(packageName)
                && isSafeGameMode(previousGameMode)
                && gameModeControlled) {
            String expectedMode = preferences.getString(
                    KEY_SESSION_CURRENT_GAME_MODE, null);
            String actualMode = currentGameModeFrom(shizuku.executeBlocking(
                    "cmd game mode list-modes " + packageName));
            if (isSafeGameMode(expectedMode)
                    && actualMode != null
                    && !expectedMode.equals(actualMode)) {
                report.skip("Modo de jogo",
                        "O modo foi alterado fora do PulseBoost e foi mantido: " + actualMode);
            } else {
                shellRestoreNeeded = true;
                ShellResult result = shizuku.executeBlocking(String.format(
                        Locale.US, "cmd game mode %s %s", previousGameMode, packageName));
                if (result.isSuccess()) {
                    report.success("Modo de jogo", "Modo anterior restaurado: " + previousGameMode);
                } else {
                    shellRestoreFailed = true;
                    report.fail("Modo de jogo", usefulError(result));
                }
            }
        }

        boolean dndRestoreFailed = false;
        if (preferences.getBoolean(KEY_DND_APPLIED, false)) {
            dndRestoreFailed = !restoreDoNotDisturb(report);
        }

        if ((shellRestoreNeeded && shellRestoreFailed) || dndRestoreFailed) {
            report.fullyRestored = false;
            report.fail("Restauração pendente",
                    "Inicie o Shizuku e toque em Restaurar novamente.");
        } else {
            SessionHistory.complete(
                    preferences, preferences.getLong(KEY_SESSION_STARTED, 0L));
            clearSession();
            report.success("Sessão", "Todos os ajustes temporários foram encerrados");
        }
        return report;
    }

    ShellResult prepareGame(String packageName) {
        if (!isSafePackage(packageName)) {
            return new ShellResult(-1, "Pacote do jogo inválido");
        }
        return shizuku.executeBlocking(
                "cmd package compile -m speed -f " + packageName);
    }

    boolean canApplyThermalGuard() {
        return hasActiveSession()
                && preferences.getBoolean(KEY_SESSION_FRAME_SENSE_ACTIVE, false)
                && preferences.getBoolean(KEY_SESSION_GAME_MODE_CONTROLLED, false)
                && "performance".equals(preferences.getString(
                        KEY_SESSION_CURRENT_GAME_MODE, null))
                && !preferences.getBoolean(KEY_THERMAL_GUARD_APPLIED, false);
    }

    boolean isFrameSenseActive() {
        return hasActiveSession()
                && preferences.getBoolean(KEY_SESSION_FRAME_SENSE_ACTIVE, false);
    }

    ShellResult applyThermalGuard() {
        if (!canApplyThermalGuard()) {
            return new ShellResult(2, "Proteção térmica não necessária");
        }
        String packageName = preferences.getString(KEY_SESSION_PACKAGE, null);
        if (!isSafePackage(packageName)) {
            return new ShellResult(-1, "Pacote do jogo inválido");
        }
        ShellResult query = shizuku.executeBlocking(
                "cmd game mode list-modes " + packageName);
        String actualMode = currentGameModeFrom(query);
        if (actualMode == null) {
            return new ShellResult(2,
                    "Não foi possível verificar o modo atual; nenhuma alteração térmica foi feita");
        }
        if (!"performance".equals(actualMode)) {
            preferences.edit()
                    .putBoolean(KEY_SESSION_GAME_MODE_CONTROLLED, false)
                    .remove(KEY_SESSION_CURRENT_GAME_MODE)
                    .apply();
            return new ShellResult(2,
                    "O modo mudou fora do PulseBoost; nenhuma alteração térmica foi feita");
        }
        ShellResult result = shizuku.executeBlocking(
                "cmd game mode standard " + packageName);
        if (result.isSuccess()) {
            preferences.edit()
                    .putString(KEY_SESSION_CURRENT_GAME_MODE, "standard")
                    .putBoolean(KEY_THERMAL_GUARD_APPLIED, true)
                    .apply();
        }
        return result;
    }

    boolean wasThermalGuardApplied() {
        return preferences.getBoolean(KEY_THERMAL_GUARD_APPLIED, false);
    }

    private void applyGameMode(String packageName, String desiredMode, Report report) {
        String reportTitle = "performance".equals(desiredMode)
                ? "Modo de jogo Performance"
                : "Modo de jogo Estável";
        ShellResult query = shizuku.executeBlocking("cmd game mode list-modes " + packageName);
        if (!query.isSuccess()) {
            report.skip(reportTitle,
                    "O sistema Samsung não disponibilizou essa função: " + usefulError(query));
            return;
        }

        Matcher matcher = GAME_MODE_PATTERN.matcher(query.output);
        if (!matcher.find()) {
            report.skip(reportTitle, "O formato retornado pelo sistema é desconhecido");
            return;
        }

        String current = normalizeGameMode(matcher.group(1));
        String available = matcher.group(2).toLowerCase(Locale.US);
        if ("performance".equals(desiredMode)
                && !available.contains("performance")
                && !containsModeNumber(available, "2")) {
            report.skip(reportTitle, "Este jogo ou aparelho não oferece esse modo");
            return;
        }

        if (isSafeGameMode(current)) {
            preferences.edit().putString(KEY_PREV_GAME_MODE, current).apply();
        }
        if (desiredMode.equals(current)) {
            preferences.edit()
                    .putBoolean(KEY_SESSION_GAME_MODE_CONTROLLED, false)
                    .remove(KEY_SESSION_CURRENT_GAME_MODE)
                    .apply();
            report.success(reportTitle, "Já estava ativo");
            return;
        }

        ShellResult set = shizuku.executeBlocking(
                "cmd game mode " + desiredMode + " " + packageName);
        if (set.isSuccess()) {
            preferences.edit()
                    .putBoolean(KEY_SESSION_GAME_MODE_CONTROLLED, true)
                    .putString(KEY_SESSION_CURRENT_GAME_MODE, desiredMode)
                    .apply();
        }
        addCommandResult(report, reportTitle, set,
                "performance".equals(desiredMode)
                        ? "Ativado apenas durante a sessão"
                        : "Priorizando estabilidade térmica nesta sessão");
    }

    private void captureFrameStats(String packageName, Report report) {
        if (!preferences.getBoolean(KEY_SESSION_FRAME_SENSE_ACTIVE, false)
                || preferences.getBoolean(KEY_FRAME_ANALYSIS_CAPTURED, false)) {
            return;
        }
        if (!isSafePackage(packageName)) {
            report.skip("FrameSense", "Pacote inválido; análise de frames ignorada");
            return;
        }

        ShellResult result = shizuku.executeBlocking(
                "dumpsys gfxinfo " + packageName + " framestats");
        if (!result.isSuccess()) {
            report.skip("FrameSense",
                    "O Android não liberou a análise desta partida: " + usefulError(result));
            return;
        }

        preferences.edit().putBoolean(KEY_FRAME_ANALYSIS_CAPTURED, true).apply();
        FrameStatsAnalyzer.Result analysis = FrameStatsAnalyzer.parse(
                result.output,
                SessionHistory.currentTargetFps(preferences));
        if (analysis.available) {
            SessionHistory.storeFrameResult(preferences, analysis);
            report.success("FrameSense", analysis.rating() + " • " + analysis.summary());
        } else {
            report.skip("FrameSense", analysis.summary());
        }
    }

    private void applyAnimations(Report report) {
        boolean success = captureAndSetAnimation(
                KEY_PREV_WINDOW_ANIM, "window_animation_scale", report);
        success &= captureAndSetAnimation(
                KEY_PREV_TRANSITION_ANIM, "transition_animation_scale", report);
        success &= captureAndSetAnimation(
                KEY_PREV_ANIMATOR, "animator_duration_scale", report);
        if (success) {
            report.success("Transições a 0,5×", "Menus do sistema temporariamente mais rápidos");
        }
    }

    private boolean captureAndSetAnimation(
            String preferenceKey, String setting, Report report) {
        String previous = readSetting("settings get global " + setting);
        if (isSafeSettingValue(previous)) {
            preferences.edit().putString(preferenceKey, previous).apply();
        }
        ShellResult result = shizuku.executeBlocking(
                "settings put global " + setting + " 0.5");
        if (!result.isSuccess()) {
            report.fail("Transições a 0,5×", usefulError(result));
            return false;
        }
        return true;
    }

    private boolean restoreAnimation(
            String preferenceKey,
            String setting,
            String reportTitle,
            Report report) {
        String previous = preferences.getString(preferenceKey, null);
        if (!isSafeSettingValue(previous)) {
            return true;
        }
        ShellResult result = shizuku.executeBlocking(
                restoreSettingCommand(setting, previous));
        if (result.isSuccess()) {
            report.success(reportTitle, "Valor anterior restaurado");
            return true;
        }
        report.fail(reportTitle, usefulError(result));
        return false;
    }

    private void applyDoNotDisturb(Report report) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.isNotificationPolicyAccessGranted()) {
            report.skip("Modo foco", "Conceda acesso ao Não Perturbe para usar esta opção");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT < 35) {
                preferences.edit()
                        .putInt(KEY_PREV_DND, manager.getCurrentInterruptionFilter())
                        .apply();
            }
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            preferences.edit().putBoolean(KEY_DND_APPLIED, true).apply();
            report.success("Modo foco", "Notificações comuns silenciadas durante o jogo");
        } catch (Throwable error) {
            report.fail("Modo foco", "O Android recusou o ajuste: " + error.getMessage());
        }
    }

    private boolean restoreDoNotDisturb(Report report) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.isNotificationPolicyAccessGranted()) {
            report.fail("Modo foco", "Acesso ao Não Perturbe não está disponível para restaurar");
            return false;
        }
        try {
            int filter = Build.VERSION.SDK_INT >= 35
                    ? NotificationManager.INTERRUPTION_FILTER_ALL
                    : preferences.getInt(
                            KEY_PREV_DND, NotificationManager.INTERRUPTION_FILTER_ALL);
            manager.setInterruptionFilter(filter);
            report.success("Modo foco", "Estado anterior restaurado");
            return true;
        } catch (Throwable error) {
            report.fail("Modo foco", "Falha ao restaurar: " + error.getMessage());
            return false;
        }
    }

    private static String restoreSettingCommand(String setting, String previous) {
        return "null".equals(previous)
                ? "settings delete global " + setting
                : "settings put global " + setting + " " + previous;
    }

    private String readSetting(String command) {
        ShellResult result = shizuku.executeBlocking(command);
        return result.isSuccess() ? result.output.trim() : null;
    }

    private static void addCommandResult(
            Report report, String title, ShellResult result, String successDetail) {
        if (result.isSuccess()) {
            report.success(title, successDetail);
        } else {
            report.fail(title, usefulError(result));
        }
    }

    private static String usefulError(ShellResult result) {
        if (result.output == null || result.output.trim().isEmpty()) {
            return "Comando não suportado (código " + result.exitCode + ")";
        }
        String compact = result.output.replace('\n', ' ').trim();
        return compact.length() > 180 ? compact.substring(0, 180) + "…" : compact;
    }

    private static boolean isSafePackage(String packageName) {
        return packageName != null && PACKAGE_PATTERN.matcher(packageName).matches();
    }

    private static boolean isSafeSettingValue(String value) {
        return value != null && SETTING_VALUE_PATTERN.matcher(value).matches();
    }

    private static boolean isSafeGameMode(String value) {
        return "standard".equals(value)
                || "performance".equals(value)
                || "battery".equals(value)
                || "custom".equals(value);
    }

    private static String normalizeGameMode(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        if ("1".equals(normalized)) {
            return "standard";
        }
        if ("2".equals(normalized)) {
            return "performance";
        }
        if ("3".equals(normalized)) {
            return "battery";
        }
        return normalized;
    }

    private static String currentGameModeFrom(ShellResult result) {
        if (result == null || !result.isSuccess()) {
            return null;
        }
        Matcher matcher = GAME_MODE_PATTERN.matcher(result.output);
        if (!matcher.find()) {
            return null;
        }
        String mode = normalizeGameMode(matcher.group(1));
        return isSafeGameMode(mode) ? mode : null;
    }

    private static boolean containsModeNumber(String available, String mode) {
        return Pattern.compile("(?:^|[,\\s])" + mode + "(?:$|[,\\s])")
                .matcher(available)
                .find();
    }

    private void clearSession() {
        preferences.edit()
                .putBoolean(KEY_SESSION_ACTIVE, false)
                .remove(KEY_SESSION_PACKAGE)
                .remove(KEY_SESSION_LABEL)
                .remove(KEY_SESSION_STARTED)
                .remove(KEY_PREV_LOW_POWER)
                .remove(KEY_PREV_GAME_MODE)
                .remove(KEY_PREV_WINDOW_ANIM)
                .remove(KEY_PREV_TRANSITION_ANIM)
                .remove(KEY_PREV_ANIMATOR)
                .remove(KEY_DND_APPLIED)
                .remove(KEY_PREV_DND)
                .remove(KEY_SESSION_FRAME_SENSE_ACTIVE)
                .remove(KEY_SESSION_GAME_MODE_CONTROLLED)
                .remove(KEY_SESSION_CURRENT_GAME_MODE)
                .remove(KEY_THERMAL_GUARD_APPLIED)
                .remove(KEY_FRAME_ANALYSIS_CAPTURED)
                .apply();
    }
}
