package io.github.astromg01.pulseboost;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements ShizukuController.Listener {
    private static final long STATS_REFRESH_MS = 5000L;
    private static final int REQUEST_NOTIFICATIONS = 7012;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService appLoader = Executors.newSingleThreadExecutor();
    private final List<GameRepository.GameApp> installedApps = new ArrayList<>();

    private SharedPreferences preferences;
    private ShizukuController shizuku;
    private Optimizer optimizer;

    private TextView readinessScore;
    private TextView readinessTitle;
    private TextView readinessDetail;
    private TextView smartProfileTitle;
    private TextView smartProfileDetail;
    private TextView ramValue;
    private TextView temperatureValue;
    private TextView storageValue;
    private TextView batteryValue;
    private TextView frameTargetValue;
    private TextView frameDisplayValue;
    private TextView frameBudgetValue;
    private TextView frameStrategyValue;
    private TextView frameQualityValue;
    private TextView shizukuDot;
    private TextView shizukuStatus;
    private TextView shizukuDetail;
    private TextView shizukuAction;
    private ImageView selectedGameIcon;
    private TextView selectedGameLabel;
    private TextView selectedGamePackage;
    private TextView selectGameButton;
    private TextView prepareGameButton;
    private TextView optimizeButton;
    private TextView restoreButton;
    private TextView reportButton;
    private TextView dndAccessButton;
    private TextView usageAccessStatus;
    private TextView usageAccessAction;
    private TextView batteryProtectionStatus;
    private TextView batteryProtectionAction;
    private LinearLayout sessionCard;
    private TextView sessionDetail;
    private LinearLayout historyCard;
    private TextView historyTitle;
    private TextView historyDetail;

    private Switch adaptiveSwitch;
    private Switch frameSenseSwitch;
    private Switch trimCacheSwitch;
    private Switch disableSaverSwitch;
    private Switch gameModeSwitch;
    private Switch dndSwitch;
    private Switch animationsSwitch;
    private Switch backgroundServiceSwitch;
    private Switch backgroundMonitorSwitch;

    private String selectedPackage;
    private String selectedLabel;
    private Drawable selectedIcon;
    private boolean appsLoaded;
    private boolean busy;
    private boolean waitingForGameReturn;
    private boolean sessionServiceStarted;
    private boolean continueAfterNotificationPermission;
    private long gameLaunchedAt;
    private Optimizer.Report lastReport;

    private final Runnable statsRefresh = new Runnable() {
        @Override
        public void run() {
            refreshDeviceStats();
            updateSessionCard();
            updateHistoryCard();
            mainHandler.postDelayed(this, STATS_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        preferences = getSharedPreferences(Optimizer.PREFS, Context.MODE_PRIVATE);
        shizuku = ShizukuController.get(this);
        optimizer = new Optimizer(this, shizuku);
        selectedPackage = preferences.getString(Optimizer.KEY_SELECTED_PACKAGE, null);
        selectedLabel = preferences.getString(Optimizer.KEY_SELECTED_LABEL, null);

        setContentView(buildScreen());
        shizuku.setListener(this);
        shizuku.refresh();
        loadInstalledApps();
        updateSelectedGame();
        updateSessionCard();
        updateDndAccess();
        updateBackgroundAccess();
        updateHistoryCard();
        refreshDeviceStats();

        if (!preferences.getBoolean("onboarding_v3_seen", false)) {
            preferences.edit().putBoolean("onboarding_v3_seen", true).apply();
            mainHandler.postDelayed(this::showFirstRunGuide, 500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        shizuku.setListener(this);
        shizuku.refresh();
        updateDndAccess();
        updateBackgroundAccess();
        updateHistoryCard();
        mainHandler.removeCallbacks(statsRefresh);
        mainHandler.post(statsRefresh);

        boolean serviceHandlesReturn = sessionServiceStarted && preferences.getBoolean(
                Optimizer.KEY_BACKGROUND_SERVICE, true)
                && preferences.getBoolean(Optimizer.KEY_BACKGROUND_MONITOR, true)
                && UsageAccess.isGranted(this);
        if (waitingForGameReturn && serviceHandlesReturn) {
            waitingForGameReturn = false;
            updateSessionCard();
        } else if (waitingForGameReturn) {
            long elapsed = SystemClock.elapsedRealtime() - gameLaunchedAt;
            if (elapsed >= 600L) {
                waitingForGameReturn = false;
                restoreSession(false);
            } else {
                mainHandler.postDelayed(() -> {
                    if (waitingForGameReturn && hasWindowFocus()) {
                        waitingForGameReturn = false;
                        restoreSession(false);
                    }
                }, 700L - elapsed);
            }
        } else {
            updateSessionCard();
        }
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(statsRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (shizuku != null) {
            shizuku.setListener(null);
        }
        appLoader.shutdownNow();
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(UiKit.BACKGROUND);
        window.setNavigationBarColor(Color.rgb(5, 7, 12));
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private View buildScreen() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(UiKit.BACKGROUND);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = UiKit.vertical(this);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 18),
                UiKit.dp(this, 20), UiKit.dp(this, 32));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader());
        content.addView(UiKit.spacer(this, 20));
        content.addView(buildReadinessCard());
        content.addView(UiKit.spacer(this, 13));
        content.addView(buildMetricsGrid());
        content.addView(UiKit.spacer(this, 14));
        content.addView(buildFrameSenseCard());

        sessionCard = buildSessionCard();
        content.addView(UiKit.spacer(this, 14));
        content.addView(sessionCard);

        historyCard = buildHistoryCard();
        content.addView(UiKit.spacer(this, 12));
        content.addView(historyCard);

        content.addView(UiKit.spacer(this, 24));
        content.addView(UiKit.sectionTitle(this, "Acesso avançado"));
        content.addView(UiKit.spacer(this, 9));
        content.addView(buildShizukuCard());

        content.addView(UiKit.spacer(this, 24));
        content.addView(UiKit.sectionTitle(this, "Execução inteligente"));
        content.addView(UiKit.spacer(this, 9));
        content.addView(buildBackgroundCard());

        content.addView(UiKit.spacer(this, 24));
        content.addView(UiKit.sectionTitle(this, "Seu jogo"));
        content.addView(UiKit.spacer(this, 9));
        content.addView(buildGameCard());

        content.addView(UiKit.spacer(this, 24));
        content.addView(UiKit.sectionTitle(this, "Perfil da sessão"));
        content.addView(UiKit.spacer(this, 9));
        content.addView(buildOptionsCard());

        content.addView(UiKit.spacer(this, 18));
        optimizeButton = UiKit.button(
                this, "Otimizar e jogar", UiKit.PURPLE, Color.WHITE);
        optimizeButton.setTextSize(15);
        optimizeButton.setMinHeight(UiKit.dp(this, 58));
        optimizeButton.setOnClickListener(view -> onOptimizeClicked());
        content.addView(optimizeButton, UiKit.matchWrap());

        LinearLayout secondaryActions = UiKit.horizontal(this);
        secondaryActions.setGravity(Gravity.CENTER);
        prepareGameButton = UiKit.button(
                this, "Preparar jogo", UiKit.SURFACE_ALT, UiKit.TEXT);
        restoreButton = UiKit.button(
                this, "Restaurar", UiKit.SURFACE_ALT, UiKit.TEXT);
        prepareGameButton.setOnClickListener(view -> onPrepareGameClicked());
        restoreButton.setOnClickListener(view -> restoreSession(true));
        LinearLayout.LayoutParams leftActionParams = UiKit.weight(1f);
        leftActionParams.setMargins(0, 0, UiKit.dp(this, 5), 0);
        LinearLayout.LayoutParams rightActionParams = UiKit.weight(1f);
        rightActionParams.setMargins(UiKit.dp(this, 5), 0, 0, 0);
        secondaryActions.addView(prepareGameButton, leftActionParams);
        secondaryActions.addView(restoreButton, rightActionParams);
        content.addView(UiKit.spacer(this, 10));
        content.addView(secondaryActions, UiKit.matchWrap());

        reportButton = UiKit.button(
                this, "Ver último relatório", Color.TRANSPARENT, UiKit.MUTED);
        reportButton.setBackground(UiKit.ripple(UiKit.BACKGROUND, UiKit.dp(this, 14)));
        reportButton.setVisibility(View.GONE);
        reportButton.setOnClickListener(view -> {
            if (lastReport != null) {
                showReport(lastReport);
            }
        });
        content.addView(reportButton, UiKit.matchWrap());

        TextView privacy = UiKit.text(this,
                "Sem anúncios • sem internet • sem captura de tela • dados locais\n"
                        + "O PulseBoost não remove limites térmicos nem promete FPS impossível.\n"
                        + "Criado e mantido por @astromg01.",
                11.5f, UiKit.MUTED, false);
        privacy.setGravity(Gravity.CENTER);
        privacy.setLineSpacing(UiKit.dp(this, 3), 1f);
        content.addView(UiKit.spacer(this, 15));
        content.addView(privacy, UiKit.matchWrap());

        updatePrimaryButtons();
        return scrollView;
    }

    private View buildHeader() {
        LinearLayout header = UiKit.horizontal(this);

        ImageView logo = new ImageView(this);
        logo.setImageResource(io.github.astromg01.pulseboost.R.drawable.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                UiKit.dp(this, 48), UiKit.dp(this, 48));
        header.addView(logo, logoParams);

        LinearLayout titleGroup = UiKit.vertical(this);
        TextView title = UiKit.text(this, "PulseBoost", 23, UiKit.TEXT, true);
        title.setLetterSpacing(-0.02f);
        TextView subtitle = UiKit.text(this,
                "Game Mode para Galaxy A06", 12, UiKit.MUTED, false);
        titleGroup.addView(title);
        titleGroup.addView(UiKit.spacer(this, 3));
        titleGroup.addView(subtitle);
        LinearLayout.LayoutParams titleParams = UiKit.weight(1f);
        titleParams.setMargins(UiKit.dp(this, 12), 0, UiKit.dp(this, 8), 0);
        header.addView(titleGroup, titleParams);

        TextView badge = UiKit.pill(this, "A06  •  V0.3.1", UiKit.PURPLE_DARK, UiKit.PURPLE);
        header.addView(badge);
        return header;
    }

    private View buildReadinessCard() {
        LinearLayout card = UiKit.card(this);
        card.setBackground(UiKit.rounded(
                Color.rgb(20, 24, 38), UiKit.dp(this, 22), Color.rgb(47, 42, 72), UiKit.dp(this, 1)));

        LinearLayout row = UiKit.horizontal(this);
        readinessScore = UiKit.text(this, "--", 24, UiKit.CYAN, true);
        readinessScore.setGravity(Gravity.CENTER);
        readinessScore.setBackground(UiKit.rounded(
                UiKit.GREEN_DARK, UiKit.dp(this, 99), Color.rgb(38, 107, 92), UiKit.dp(this, 1)));
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(
                UiKit.dp(this, 72), UiKit.dp(this, 72));
        row.addView(readinessScore, scoreParams);

        LinearLayout description = UiKit.vertical(this);
        TextView eyebrow = UiKit.text(this, "DIAGNÓSTICO AGORA", 10.5f, UiKit.PURPLE, true);
        eyebrow.setLetterSpacing(0.11f);
        readinessTitle = UiKit.text(this, "Analisando aparelho…", 19, UiKit.TEXT, true);
        readinessDetail = UiKit.text(this, "Aguarde um instante.", 12.5f, UiKit.MUTED, false);
        description.addView(eyebrow);
        description.addView(UiKit.spacer(this, 5));
        description.addView(readinessTitle);
        description.addView(UiKit.spacer(this, 5));
        description.addView(readinessDetail);
        LinearLayout.LayoutParams descriptionParams = UiKit.weight(1f);
        descriptionParams.setMargins(UiKit.dp(this, 15), 0, 0, 0);
        row.addView(description, descriptionParams);
        card.addView(row, UiKit.matchWrap());

        addDivider(card);
        LinearLayout smartRow = UiKit.vertical(this);
        TextView smartEyebrow = UiKit.text(
                this, "PERFIL RECOMENDADO", 10.5f, UiKit.CYAN, true);
        smartEyebrow.setLetterSpacing(0.1f);
        smartProfileTitle = UiKit.text(this, "Calculando…", 15.5f, UiKit.TEXT, true);
        smartProfileDetail = UiKit.text(this,
                "O motor adapta potência, memória e calor.", 11.5f, UiKit.MUTED, false);
        smartRow.addView(smartEyebrow);
        smartRow.addView(UiKit.spacer(this, 5));
        smartRow.addView(smartProfileTitle);
        smartRow.addView(UiKit.spacer(this, 4));
        smartRow.addView(smartProfileDetail);
        card.addView(smartRow, UiKit.matchWrap());
        return card;
    }

    private View buildMetricsGrid() {
        LinearLayout grid = UiKit.vertical(this);
        LinearLayout firstRow = UiKit.horizontal(this);
        LinearLayout secondRow = UiKit.horizontal(this);

        ramValue = UiKit.text(this, "--", 18, UiKit.TEXT, true);
        temperatureValue = UiKit.text(this, "--", 18, UiKit.TEXT, true);
        storageValue = UiKit.text(this, "--", 18, UiKit.TEXT, true);
        batteryValue = UiKit.text(this, "--", 18, UiKit.TEXT, true);

        View ramCard = metricCard("RAM livre", ramValue, "memória disponível");
        View tempCard = metricCard("Temperatura", temperatureValue, "bateria • proxy térmico");
        View storageCard = metricCard("Armazenamento", storageValue, "espaço livre");
        View batteryCard = metricCard("Energia", batteryValue, "estado atual");

        LinearLayout.LayoutParams firstLeft = UiKit.weight(1f);
        firstLeft.setMargins(0, 0, UiKit.dp(this, 5), 0);
        LinearLayout.LayoutParams firstRight = UiKit.weight(1f);
        firstRight.setMargins(UiKit.dp(this, 5), 0, 0, 0);
        firstRow.addView(ramCard, firstLeft);
        firstRow.addView(tempCard, firstRight);

        LinearLayout.LayoutParams secondLeft = UiKit.weight(1f);
        secondLeft.setMargins(0, 0, UiKit.dp(this, 5), 0);
        LinearLayout.LayoutParams secondRight = UiKit.weight(1f);
        secondRight.setMargins(UiKit.dp(this, 5), 0, 0, 0);
        secondRow.addView(storageCard, secondLeft);
        secondRow.addView(batteryCard, secondRight);

        grid.addView(firstRow, UiKit.matchWrap());
        grid.addView(UiKit.spacer(this, 10));
        grid.addView(secondRow, UiKit.matchWrap());
        return grid;
    }

    private View buildFrameSenseCard() {
        LinearLayout card = UiKit.card(this);
        card.setBackground(UiKit.rounded(
                Color.rgb(24, 22, 43), UiKit.dp(this, 20),
                Color.rgb(76, 61, 128), UiKit.dp(this, 1)));

        LinearLayout header = UiKit.horizontal(this);
        LinearLayout titleGroup = UiKit.vertical(this);
        TextView eyebrow = UiKit.text(this, "FRAMESENSE", 10.5f, UiKit.PURPLE, true);
        eyebrow.setLetterSpacing(0.11f);
        TextView title = UiKit.text(this, "Alvo sustentável", 16, UiKit.TEXT, true);
        titleGroup.addView(eyebrow);
        titleGroup.addView(UiKit.spacer(this, 4));
        titleGroup.addView(title);
        header.addView(titleGroup, UiKit.weight(1f));
        header.addView(UiKit.pill(
                this, "SEM INTERPOLAÇÃO", Color.rgb(47, 39, 79), UiKit.PURPLE));
        card.addView(header, UiKit.matchWrap());

        frameTargetValue = UiKit.text(this, "-- FPS", 31, UiKit.CYAN, true);
        card.addView(UiKit.spacer(this, 12));
        card.addView(frameTargetValue, UiKit.matchWrap());

        LinearLayout metrics = UiKit.horizontal(this);
        LinearLayout displayGroup = UiKit.vertical(this);
        TextView displayLabel = UiKit.text(this, "TELA", 9.5f, UiKit.MUTED, true);
        frameDisplayValue = UiKit.text(this, "-- Hz", 14, UiKit.TEXT, true);
        displayGroup.addView(displayLabel);
        displayGroup.addView(UiKit.spacer(this, 3));
        displayGroup.addView(frameDisplayValue);
        metrics.addView(displayGroup, UiKit.weight(1f));

        LinearLayout budgetGroup = UiKit.vertical(this);
        TextView budgetLabel = UiKit.text(this, "ORÇAMENTO", 9.5f, UiKit.MUTED, true);
        frameBudgetValue = UiKit.text(this, "-- ms/quadro", 14, UiKit.TEXT, true);
        budgetGroup.addView(budgetLabel);
        budgetGroup.addView(UiKit.spacer(this, 3));
        budgetGroup.addView(frameBudgetValue);
        metrics.addView(budgetGroup, UiKit.weight(1f));
        card.addView(UiKit.spacer(this, 10));
        card.addView(metrics, UiKit.matchWrap());

        addDivider(card);
        frameStrategyValue = UiKit.text(
                this, "Analisando temperatura e memória…", 13, UiKit.TEXT, true);
        frameQualityValue = UiKit.text(this,
                "A recomendação não força o jogo a entregar esse FPS.",
                11.5f, UiKit.MUTED, false);
        card.addView(frameStrategyValue, UiKit.matchWrap());
        card.addView(UiKit.spacer(this, 4));
        card.addView(frameQualityValue, UiKit.matchWrap());
        card.addView(UiKit.spacer(this, 9));
        TextView safety = UiKit.text(this,
                "Não injeta quadros, não usa overlay e não altera arquivos do jogo.",
                10.5f, Color.rgb(144, 151, 172), false);
        card.addView(safety, UiKit.matchWrap());
        return card;
    }

    private View metricCard(String label, TextView value, String footnote) {
        LinearLayout card = UiKit.card(this);
        card.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 14),
                UiKit.dp(this, 14), UiKit.dp(this, 14));
        TextView labelView = UiKit.text(this, label, 11.5f, UiKit.MUTED, true);
        TextView footnoteView = UiKit.text(this, footnote, 10.5f, Color.rgb(112, 122, 145), false);
        card.addView(labelView);
        card.addView(UiKit.spacer(this, 7));
        card.addView(value);
        card.addView(UiKit.spacer(this, 4));
        card.addView(footnoteView);
        return card;
    }

    private LinearLayout buildSessionCard() {
        LinearLayout card = UiKit.card(this);
        card.setBackground(UiKit.rounded(
                Color.rgb(47, 35, 24), UiKit.dp(this, 18), Color.rgb(112, 78, 35), UiKit.dp(this, 1)));
        LinearLayout row = UiKit.horizontal(this);
        LinearLayout copy = UiKit.vertical(this);
        TextView title = UiKit.text(this, "Sessão temporária ativa", 15, UiKit.YELLOW, true);
        sessionDetail = UiKit.text(this,
                "Volte ao app para restaurar os ajustes.", 11.5f, Color.rgb(220, 196, 158), false);
        copy.addView(title);
        copy.addView(UiKit.spacer(this, 4));
        copy.addView(sessionDetail);
        row.addView(copy, UiKit.weight(1f));
        TextView action = UiKit.pill(this, "RESTAURAR", Color.rgb(80, 56, 28), UiKit.YELLOW);
        action.setClickable(true);
        action.setFocusable(true);
        action.setOnClickListener(view -> restoreSession(true));
        row.addView(action);
        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private LinearLayout buildHistoryCard() {
        LinearLayout card = UiKit.card(this);
        card.setBackground(UiKit.rounded(
                Color.rgb(16, 31, 35), UiKit.dp(this, 18),
                Color.rgb(35, 88, 82), UiKit.dp(this, 1)));
        TextView eyebrow = UiKit.text(this, "ÚLTIMA PARTIDA", 10.5f, UiKit.CYAN, true);
        eyebrow.setLetterSpacing(0.1f);
        historyTitle = UiKit.text(this, "Perfil inteligente", 15.5f, UiKit.TEXT, true);
        historyDetail = UiKit.text(this, "Sem histórico ainda", 11.5f, UiKit.MUTED, false);
        card.addView(eyebrow);
        card.addView(UiKit.spacer(this, 5));
        card.addView(historyTitle);
        card.addView(UiKit.spacer(this, 5));
        card.addView(historyDetail);
        return card;
    }

    private View buildShizukuCard() {
        LinearLayout card = UiKit.card(this);
        LinearLayout row = UiKit.horizontal(this);

        shizukuDot = UiKit.text(this, "●", 16, UiKit.RED, true);
        row.addView(shizukuDot);

        LinearLayout copy = UiKit.vertical(this);
        shizukuStatus = UiKit.text(this, "Shizuku desconectado", 16, UiKit.TEXT, true);
        shizukuDetail = UiKit.text(this, "Inicie o serviço para liberar a otimização.",
                11.5f, UiKit.MUTED, false);
        copy.addView(shizukuStatus);
        copy.addView(UiKit.spacer(this, 4));
        copy.addView(shizukuDetail);
        LinearLayout.LayoutParams copyParams = UiKit.weight(1f);
        copyParams.setMargins(UiKit.dp(this, 11), 0, UiKit.dp(this, 10), 0);
        row.addView(copy, copyParams);

        shizukuAction = UiKit.pill(this, "CONECTAR", UiKit.PURPLE_DARK, UiKit.PURPLE);
        shizukuAction.setClickable(true);
        shizukuAction.setFocusable(true);
        shizukuAction.setOnClickListener(view -> handleShizukuAction());
        row.addView(shizukuAction);
        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private View buildBackgroundCard() {
        LinearLayout card = UiKit.card(this);
        backgroundServiceSwitch = addOption(card,
                "Serviço persistente durante o jogo",
                "Mantém o PulseBoost prioritário e mostra controles na notificação.",
                Optimizer.KEY_BACKGROUND_SERVICE,
                true);
        addDivider(card);
        backgroundMonitorSwitch = addOption(card,
                "Restauração automática",
                "Detecta quando o jogo fecha e encerra o perfil após um intervalo seguro.",
                Optimizer.KEY_BACKGROUND_MONITOR,
                true);
        addDivider(card);

        LinearLayout usageRow = buildSettingsRow(
                "Acesso ao uso",
                "Necessário somente para detectar o fim da partida.");
        LinearLayout usageCopy = (LinearLayout) usageRow.getChildAt(0);
        usageAccessStatus = (TextView) usageCopy.getChildAt(2);
        usageAccessAction = UiKit.pill(this, "ATIVAR", UiKit.PURPLE_DARK, UiKit.PURPLE);
        usageAccessAction.setClickable(true);
        usageAccessAction.setFocusable(true);
        usageAccessAction.setOnClickListener(view -> openUsageAccess());
        usageRow.addView(usageAccessAction);
        card.addView(usageRow, UiKit.matchWrap());

        addDivider(card);
        LinearLayout batteryRow = buildSettingsRow(
                "Proteção contra suspensão",
                "Na Samsung, deixe a bateria do PulseBoost como Sem restrições.");
        LinearLayout batteryCopy = (LinearLayout) batteryRow.getChildAt(0);
        batteryProtectionStatus = (TextView) batteryCopy.getChildAt(2);
        batteryProtectionAction = UiKit.pill(this, "ABRIR", UiKit.PURPLE_DARK, UiKit.PURPLE);
        batteryProtectionAction.setClickable(true);
        batteryProtectionAction.setFocusable(true);
        batteryProtectionAction.setOnClickListener(view -> showBatteryProtectionGuide());
        batteryRow.addView(batteryProtectionAction);
        card.addView(batteryRow, UiKit.matchWrap());
        return card;
    }

    private LinearLayout buildSettingsRow(String title, String detail) {
        LinearLayout row = UiKit.horizontal(this);
        LinearLayout copy = UiKit.vertical(this);
        TextView titleView = UiKit.text(this, title, 14.5f, UiKit.TEXT, true);
        TextView detailView = UiKit.text(this, detail, 11.5f, UiKit.MUTED, false);
        copy.addView(titleView);
        copy.addView(UiKit.spacer(this, 4));
        copy.addView(detailView);
        LinearLayout.LayoutParams copyParams = UiKit.weight(1f);
        copyParams.setMargins(0, 0, UiKit.dp(this, 12), 0);
        row.addView(copy, copyParams);
        return row;
    }

    private View buildGameCard() {
        LinearLayout card = UiKit.card(this);
        LinearLayout row = UiKit.horizontal(this);

        selectedGameIcon = new ImageView(this);
        selectedGameIcon.setImageResource(io.github.astromg01.pulseboost.R.drawable.ic_launcher);
        selectedGameIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        selectedGameIcon.setBackground(UiKit.rounded(
                UiKit.SURFACE_LIGHT, UiKit.dp(this, 14), Color.TRANSPARENT, 0));
        selectedGameIcon.setPadding(UiKit.dp(this, 5), UiKit.dp(this, 5),
                UiKit.dp(this, 5), UiKit.dp(this, 5));
        row.addView(selectedGameIcon, new LinearLayout.LayoutParams(
                UiKit.dp(this, 58), UiKit.dp(this, 58)));

        LinearLayout copy = UiKit.vertical(this);
        selectedGameLabel = UiKit.text(this, "Nenhum jogo selecionado", 16, UiKit.TEXT, true);
        selectedGamePackage = UiKit.text(this,
                "Escolha o jogo que será aberto", 11.5f, UiKit.MUTED, false);
        selectedGamePackage.setSingleLine(true);
        copy.addView(selectedGameLabel);
        copy.addView(UiKit.spacer(this, 5));
        copy.addView(selectedGamePackage);
        LinearLayout.LayoutParams copyParams = UiKit.weight(1f);
        copyParams.setMargins(UiKit.dp(this, 13), 0, UiKit.dp(this, 8), 0);
        row.addView(copy, copyParams);

        selectGameButton = UiKit.pill(this, "ESCOLHER", UiKit.PURPLE_DARK, UiKit.PURPLE);
        selectGameButton.setClickable(true);
        selectGameButton.setFocusable(true);
        selectGameButton.setOnClickListener(view -> showGameChooser());
        row.addView(selectGameButton);
        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private View buildOptionsCard() {
        LinearLayout card = UiKit.card(this);
        adaptiveSwitch = addOption(card,
                "Motor inteligente adaptativo",
                "Escolhe entre potência, memória e estabilidade térmica a cada partida.",
                Optimizer.KEY_ADAPTIVE,
                true);
        addDivider(card);
        frameSenseSwitch = addOption(card,
                "FrameSense inteligente",
                "Recomenda um alvo divisível pela tela, analisa frames ao terminar e protege contra calor.",
                Optimizer.KEY_FRAME_SENSE,
                true);
        addDivider(card);
        trimCacheSwitch = addOption(card,
                "Liberar processos em cache",
                "Pede ao Android para encerrar somente apps armazenados em segundo plano.",
                Optimizer.KEY_TRIM_CACHE,
                true);
        addDivider(card);
        disableSaverSwitch = addOption(card,
                "Desligar economia de bateria",
                "Remove a limitação de energia durante a sessão e restaura ao terminar.",
                Optimizer.KEY_DISABLE_SAVER,
                true);
        addDivider(card);
        gameModeSwitch = addOption(card,
                "Modo de jogo Performance",
                "Ativa apenas se o Android/Samsung e o jogo realmente oferecerem esse modo.",
                Optimizer.KEY_GAME_MODE,
                true);
        addDivider(card);
        dndSwitch = addOption(card,
                "Modo foco",
                "Silencia notificações comuns sem bloquear alarmes e chamadas prioritárias.",
                Optimizer.KEY_DND,
                true);

        dndAccessButton = UiKit.text(this, "CONCEDER ACESSO AO NÃO PERTURBE",
                11, UiKit.PURPLE, true);
        dndAccessButton.setLetterSpacing(0.06f);
        dndAccessButton.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 4));
        dndAccessButton.setClickable(true);
        dndAccessButton.setFocusable(true);
        dndAccessButton.setOnClickListener(view -> openDoNotDisturbAccess());
        card.addView(dndAccessButton, UiKit.matchWrap());

        addDivider(card);
        animationsSwitch = addOption(card,
                "Transições do sistema a 0,5×",
                "Acelera menus, mas não aumenta FPS dentro do jogo. Fica desligado por padrão.",
                Optimizer.KEY_ANIMATIONS,
                false);
        return card;
    }

    private Switch addOption(
            LinearLayout parent,
            String title,
            String detail,
            String preferenceKey,
            boolean defaultValue) {
        LinearLayout row = UiKit.horizontal(this);
        row.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 5));

        LinearLayout copy = UiKit.vertical(this);
        TextView titleView = UiKit.text(this, title, 14.5f, UiKit.TEXT, true);
        TextView detailView = UiKit.text(this, detail, 11.5f, UiKit.MUTED, false);
        copy.addView(titleView);
        copy.addView(UiKit.spacer(this, 4));
        copy.addView(detailView);
        LinearLayout.LayoutParams copyParams = UiKit.weight(1f);
        copyParams.setMargins(0, 0, UiKit.dp(this, 12), 0);
        row.addView(copy, copyParams);

        Switch toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setChecked(preferences.getBoolean(preferenceKey, defaultValue));
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(
                states, new int[] {UiKit.PURPLE, Color.rgb(125, 132, 148)}));
        toggle.setTrackTintList(new ColorStateList(
                states, new int[] {Color.rgb(74, 58, 137), Color.rgb(55, 61, 75)}));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(preferenceKey, checked).apply();
            if (Optimizer.KEY_ADAPTIVE.equals(preferenceKey)
                    || Optimizer.KEY_FRAME_SENSE.equals(preferenceKey)) {
                refreshDeviceStats();
            }
            if (Optimizer.KEY_BACKGROUND_SERVICE.equals(preferenceKey)
                    || Optimizer.KEY_BACKGROUND_MONITOR.equals(preferenceKey)) {
                updateBackgroundAccess();
            }
        });
        row.addView(toggle);
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        parent.addView(row, UiKit.matchWrap());
        return toggle;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(36, 41, 56));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 1));
        params.setMargins(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 10));
        parent.addView(divider, params);
    }

    private void refreshDeviceStats() {
        DeviceStats stats;
        try {
            stats = DeviceStats.read(this);
        } catch (Throwable error) {
            return;
        }

        readinessScore.setText(String.valueOf(stats.readinessScore));
        readinessTitle.setText(stats.readinessTitle);
        readinessDetail.setText(stats.recommendation);
        ramValue.setText(DeviceStats.formatBytes(stats.availableMemory));
        temperatureValue.setText(String.format(Locale.getDefault(), "%.1f °C",
                stats.batteryTemperature));
        storageValue.setText(DeviceStats.formatBytes(stats.availableStorage));
        String energy = stats.powerSaveMode ? "Economia ativa" : "Desempenho livre";
        if (stats.batteryLevel >= 0) {
            energy = stats.batteryLevel + "% • " + energy;
        }
        batteryValue.setText(energy);

        if (smartProfileTitle != null && smartProfileDetail != null) {
            AdaptiveEngine.Plan plan = AdaptiveEngine.decide(
                    stats, Optimizer.Options.load(preferences));
            smartProfileTitle.setText(plan.title);
            smartProfileDetail.setText(plan.detail);
            smartProfileTitle.setTextColor(
                    stats.batteryTemperature >= 41.5f ? UiKit.YELLOW : UiKit.CYAN);
        }

        if (frameTargetValue != null) {
            FrameSense.Plan framePlan = FrameSense.decide(this, stats);
            frameTargetValue.setText(framePlan.targetFps + " FPS");
            frameDisplayValue.setText(framePlan.displayLabel());
            frameBudgetValue.setText(framePlan.budgetLabel());
            boolean enabled = frameSenseSwitch == null || frameSenseSwitch.isChecked();
            frameStrategyValue.setText(enabled
                    ? framePlan.title
                    : "Desativado para a próxima sessão");
            frameQualityValue.setText(enabled
                    ? framePlan.detail + " • " + framePlan.qualityHint
                    : "A prévia continua visível, mas a análise e a proteção térmica não serão usadas.");
            frameTargetValue.setTextColor(
                    stats.batteryTemperature >= 41.5f ? UiKit.YELLOW : UiKit.CYAN);
        }

        int scoreColor;
        int scoreBackground;
        int scoreStroke;
        if (stats.readinessScore >= 85) {
            scoreColor = UiKit.CYAN;
            scoreBackground = UiKit.GREEN_DARK;
            scoreStroke = Color.rgb(38, 107, 92);
        } else if (stats.readinessScore >= 65) {
            scoreColor = UiKit.YELLOW;
            scoreBackground = Color.rgb(70, 52, 27);
            scoreStroke = Color.rgb(125, 88, 36);
        } else {
            scoreColor = UiKit.RED;
            scoreBackground = Color.rgb(71, 31, 39);
            scoreStroke = Color.rgb(124, 47, 61);
        }
        readinessScore.setTextColor(scoreColor);
        readinessScore.setBackground(UiKit.rounded(
                scoreBackground, UiKit.dp(this, 99), scoreStroke, UiKit.dp(this, 1)));
        temperatureValue.setTextColor(stats.batteryTemperature >= 43f
                ? UiKit.RED
                : stats.batteryTemperature >= 39f ? UiKit.YELLOW : UiKit.TEXT);
    }

    @Override
    public void onShizukuStatusChanged(ShizukuController.Status status, String detail) {
        runOnUiThread(() -> {
            shizukuDetail.setText(detail);
            switch (status) {
                case READY:
                    shizukuDot.setTextColor(UiKit.CYAN);
                    shizukuStatus.setText("Shizuku conectado");
                    shizukuAction.setText("PRONTO");
                    shizukuAction.setTextColor(UiKit.CYAN);
                    shizukuAction.setBackground(UiKit.rounded(
                            UiKit.GREEN_DARK, UiKit.dp(this, 99), Color.TRANSPARENT, 0));
                    break;
                case PERMISSION_REQUIRED:
                    shizukuDot.setTextColor(UiKit.YELLOW);
                    shizukuStatus.setText("Falta autorização");
                    shizukuAction.setText("AUTORIZAR");
                    shizukuAction.setTextColor(UiKit.YELLOW);
                    shizukuAction.setBackground(UiKit.rounded(
                            Color.rgb(72, 53, 27), UiKit.dp(this, 99), Color.TRANSPARENT, 0));
                    break;
                case CONNECTING:
                    shizukuDot.setTextColor(UiKit.PURPLE);
                    shizukuStatus.setText("Conectando ao Shizuku");
                    shizukuAction.setText("AGUARDE");
                    break;
                case UNSUPPORTED:
                    shizukuDot.setTextColor(UiKit.RED);
                    shizukuStatus.setText("Shizuku desatualizado");
                    shizukuAction.setText("ABRIR");
                    break;
                case ERROR:
                    shizukuDot.setTextColor(UiKit.RED);
                    shizukuStatus.setText("Falha no Shizuku");
                    shizukuAction.setText("TENTAR");
                    break;
                case OFFLINE:
                default:
                    shizukuDot.setTextColor(UiKit.RED);
                    shizukuStatus.setText("Shizuku desconectado");
                    shizukuAction.setText("ABRIR");
                    shizukuAction.setTextColor(UiKit.PURPLE);
                    shizukuAction.setBackground(UiKit.rounded(
                            UiKit.PURPLE_DARK, UiKit.dp(this, 99), Color.TRANSPARENT, 0));
                    break;
            }
            updatePrimaryButtons();
        });
    }

    private void handleShizukuAction() {
        ShizukuController.Status status = shizuku.getStatus();
        if (status == ShizukuController.Status.PERMISSION_REQUIRED) {
            shizuku.requestPermission();
            return;
        }
        if (status == ShizukuController.Status.READY
                || status == ShizukuController.Status.CONNECTING
                || status == ShizukuController.Status.ERROR) {
            shizuku.refresh();
            return;
        }
        if (!shizuku.openManager()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://shizuku.rikka.app/download/")));
            } catch (Throwable error) {
                toast("Instale o Shizuku para continuar");
            }
        }
    }

    private void loadInstalledApps() {
        appsLoaded = false;
        appLoader.execute(() -> {
            List<GameRepository.GameApp> result = GameRepository.loadLauncherApps(this);
            runOnUiThread(() -> {
                installedApps.clear();
                installedApps.addAll(result);
                appsLoaded = true;
                GameRepository.GameApp selected =
                        GameRepository.findByPackage(installedApps, selectedPackage);
                if (selected != null) {
                    selectedLabel = selected.label;
                    selectedIcon = selected.icon;
                    preferences.edit()
                            .putString(Optimizer.KEY_SELECTED_LABEL, selectedLabel)
                            .apply();
                }
                updateSelectedGame();
            });
        });
    }

    private void updateSelectedGame() {
        boolean selected = selectedPackage != null && !selectedPackage.trim().isEmpty();
        if (selected) {
            selectedGameLabel.setText(selectedLabel == null ? "Jogo selecionado" : selectedLabel);
            selectedGamePackage.setText(selectedPackage);
            if (selectedIcon != null) {
                selectedGameIcon.setImageDrawable(selectedIcon);
                selectedGameIcon.setPadding(0, 0, 0, 0);
            }
            selectGameButton.setText("TROCAR");
        } else {
            selectedGameLabel.setText("Nenhum jogo selecionado");
            selectedGamePackage.setText(appsLoaded
                    ? "Toque em escolher para continuar"
                    : "Carregando seus aplicativos…");
            selectedGameIcon.setImageResource(io.github.astromg01.pulseboost.R.drawable.ic_launcher);
            selectedGameIcon.setPadding(UiKit.dp(this, 5), UiKit.dp(this, 5),
                    UiKit.dp(this, 5), UiKit.dp(this, 5));
            selectGameButton.setText("ESCOLHER");
        }
        updatePrimaryButtons();
    }

    private void showGameChooser() {
        if (!appsLoaded) {
            toast("Ainda estou carregando a lista de aplicativos");
            return;
        }
        if (installedApps.isEmpty()) {
            toast("O Android não liberou a lista de aplicativos");
            return;
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = dialogWindow.getAttributes();
            attributes.dimAmount = 0.72f;
            dialogWindow.setAttributes(attributes);
        }

        LinearLayout outer = UiKit.vertical(this);
        outer.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 28),
                UiKit.dp(this, 14), UiKit.dp(this, 24));
        outer.setGravity(Gravity.CENTER);

        LinearLayout panel = UiKit.vertical(this);
        panel.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 18),
                UiKit.dp(this, 18), UiKit.dp(this, 14));
        panel.setBackground(UiKit.rounded(
                UiKit.SURFACE, UiKit.dp(this, 23), Color.rgb(48, 54, 72), UiKit.dp(this, 1)));

        TextView title = UiKit.text(this, "Escolha o jogo", 22, UiKit.TEXT, true);
        TextView subtitle = UiKit.text(this,
                "Jogos reconhecidos pelo Android aparecem primeiro.", 12, UiKit.MUTED, false);
        panel.addView(title);
        panel.addView(UiKit.spacer(this, 5));
        panel.addView(subtitle);
        panel.addView(UiKit.spacer(this, 14));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Buscar jogo ou aplicativo");
        search.setHintTextColor(Color.rgb(111, 120, 142));
        search.setTextColor(UiKit.TEXT);
        search.setTextSize(14);
        search.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 12),
                UiKit.dp(this, 14), UiKit.dp(this, 12));
        search.setBackground(UiKit.rounded(
                UiKit.SURFACE_ALT, UiKit.dp(this, 14), Color.rgb(48, 54, 72), UiKit.dp(this, 1)));
        panel.addView(search, UiKit.matchWrap());
        panel.addView(UiKit.spacer(this, 10));

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(false);
        GameListAdapter adapter = new GameListAdapter(installedApps);
        list.setAdapter(adapter);
        panel.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView close = UiKit.button(this, "Fechar", UiKit.SURFACE_ALT, UiKit.MUTED);
        close.setOnClickListener(view -> dialog.dismiss());
        panel.addView(UiKit.spacer(this, 10));
        panel.addView(close, UiKit.matchWrap());
        outer.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            GameRepository.GameApp app = adapter.getItem(position);
            selectedPackage = app.packageName;
            selectedLabel = app.label;
            selectedIcon = app.icon;
            preferences.edit()
                    .putString(Optimizer.KEY_SELECTED_PACKAGE, selectedPackage)
                    .putString(Optimizer.KEY_SELECTED_LABEL, selectedLabel)
                    .apply();
            updateSelectedGame();
            dialog.dismiss();
        });

        dialog.setContentView(outer);
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
        });
        dialog.show();
    }

    private void onOptimizeClicked() {
        if (busy) {
            return;
        }
        if (optimizer.hasActiveSession()) {
            new AlertDialog.Builder(this)
                    .setTitle("Já existe uma sessão ativa")
                    .setMessage("Restaure os ajustes da partida anterior antes de iniciar outra otimização.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Restaurar", (dialog, which) -> restoreSession(true))
                    .show();
            return;
        }
        if (selectedPackage == null) {
            showGameChooser();
            return;
        }
        if (!shizuku.isReady()) {
            toast("Conecte e autorize o Shizuku primeiro");
            handleShizukuAction();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && preferences.getBoolean(Optimizer.KEY_BACKGROUND_SERVICE, true)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("notification_permission_explained", false)) {
            continueAfterNotificationPermission = true;
            preferences.edit().putBoolean("notification_permission_explained", true).apply();
            new AlertDialog.Builder(this)
                    .setTitle("Controles durante o jogo")
                    .setMessage("Permita notificações para manter visível o estado da sessão, "
                            + "a temperatura e o botão Restaurar. O serviço funciona sem anúncios e sem internet.")
                    .setNegativeButton("Agora não", (dialog, which) -> {
                        continueAfterNotificationPermission = false;
                        continueOptimizationChecks();
                    })
                    .setPositiveButton("Permitir", (dialog, which) ->
                            requestPermissions(
                                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                                    REQUEST_NOTIFICATIONS))
                    .show();
            return;
        }
        continueOptimizationChecks();
    }

    private void continueOptimizationChecks() {
        DeviceStats stats = DeviceStats.read(this);
        if (stats.batteryTemperature >= 43f) {
            new AlertDialog.Builder(this)
                    .setTitle("Aparelho muito quente")
                    .setMessage("A bateria está em "
                            + String.format(Locale.getDefault(), "%.1f °C", stats.batteryTemperature)
                            + ". Nessa faixa o processador pode reduzir a velocidade e causar stutter."
                            + "\n\nO mais eficaz agora é esperar esfriar.")
                    .setNegativeButton("Esperar", null)
                    .setPositiveButton("Jogar mesmo", (dialog, which) -> runOptimization())
                    .show();
            return;
        }
        runOptimization();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && continueAfterNotificationPermission) {
            continueAfterNotificationPermission = false;
            continueOptimizationChecks();
        }
    }

    private void runOptimization() {
        setBusy(true, "Otimizando…");
        Optimizer.Options options = Optimizer.Options.load(preferences);
        String packageName = selectedPackage;
        String gameLabel = selectedLabel;
        DeviceStats stats = DeviceStats.read(this);
        shizuku.runInBackground(() -> {
            Optimizer.Report report = optimizer.optimize(
                    packageName, gameLabel, options, stats);
            shizuku.postToMain(() -> {
                if (optimizer.hasActiveSession() && options.backgroundService) {
                    sessionServiceStarted = GameSessionService.start(this);
                    if (sessionServiceStarted) {
                        report.success("Execução em segundo plano",
                                UsageAccess.isGranted(this)
                                        ? "Monitor persistente e restauração automática iniciados"
                                        : "Monitor persistente iniciado; ative Acesso ao uso para restauração automática");
                    } else {
                        report.fail("Execução em segundo plano",
                                "O Android recusou o serviço; mantenha o PulseBoost sem restrições de bateria");
                    }
                } else if (optimizer.hasActiveSession()) {
                    report.skip("Execução em segundo plano", "Desativada nas opções da sessão");
                }
                setBusy(false, null);
                lastReport = report;
                reportButton.setVisibility(View.VISIBLE);
                updateSessionCard();
                launchSelectedGame(report);
            });
        });
    }

    private void launchSelectedGame(Optimizer.Report report) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedPackage);
        if (launchIntent == null) {
            toast("Não consegui abrir esse aplicativo");
            showReport(report);
            restoreSession(false);
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        toast(report.successCount() + " ajustes aplicados • abrindo " + selectedLabel);
        waitingForGameReturn = true;
        gameLaunchedAt = SystemClock.elapsedRealtime();
        try {
            startActivity(launchIntent);
        } catch (Throwable error) {
            waitingForGameReturn = false;
            toast("Falha ao abrir o jogo: " + error.getMessage());
            restoreSession(false);
        }
    }

    private void restoreSession(boolean showDetails) {
        if (busy || !optimizer.hasActiveSession()) {
            if (showDetails && !optimizer.hasActiveSession()) {
                toast("Não há uma sessão ativa para restaurar");
            }
            return;
        }
        setBusy(true, "Restaurando…");
        optimizer.setSessionEndReason(showDetails
                ? "Encerrada manualmente no PulseBoost"
                : "Retorno ao PulseBoost • restauração automática");
        shizuku.runInBackground(() -> {
            Optimizer.Report report = optimizer.restore();
            shizuku.postToMain(() -> {
                if (report.fullyRestored || !optimizer.hasActiveSession()) {
                    GameSessionService.stopMonitoring(this);
                    sessionServiceStarted = false;
                }
                setBusy(false, null);
                lastReport = report;
                reportButton.setVisibility(View.VISIBLE);
                updateSessionCard();
                updateHistoryCard();
                refreshDeviceStats();
                if (showDetails || !report.fullyRestored) {
                    showReport(report);
                } else {
                    toast("Sessão encerrada • configurações restauradas");
                }
            });
        });
    }

    private void onPrepareGameClicked() {
        if (busy) {
            return;
        }
        if (selectedPackage == null) {
            showGameChooser();
            return;
        }
        if (!shizuku.isReady()) {
            toast("Conecte o Shizuku primeiro");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Preparar " + selectedLabel + "?")
                .setMessage("O Android tentará pré-compilar o código do jogo para reduzir trabalho do JIT. "
                        + "Pode levar alguns minutos e usar mais armazenamento.\n\n"
                        + "Feche o jogo antes de continuar. Faça isso uma vez, não antes de toda partida.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Preparar", (dialog, which) -> prepareSelectedGame())
                .show();
    }

    private void prepareSelectedGame() {
        setBusy(true, "Preparando jogo…");
        String packageName = selectedPackage;
        shizuku.runInBackground(() -> {
            ShellResult result = optimizer.prepareGame(packageName);
            shizuku.postToMain(() -> {
                setBusy(false, null);
                if (result.isSuccess()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Jogo preparado")
                            .setMessage("O Android concluiu a pré-compilação. Isso pode reduzir travadas "
                                    + "causadas por compilação durante o uso, mas não corrige limite de GPU ou calor.")
                            .setPositiveButton("Boa", null)
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Não foi possível preparar")
                            .setMessage(result.output.isEmpty()
                                    ? "A Samsung não permitiu esse comando."
                                    : result.output)
                            .setPositiveButton("Entendi", null)
                            .show();
                }
            });
        });
    }

    private void showReport(Optimizer.Report report) {
        StringBuilder message = new StringBuilder();
        for (Optimizer.ReportEntry entry : report.entries) {
            String marker = entry.success ? "✓" : entry.skipped ? "—" : "!";
            message.append(marker).append(' ').append(entry.title).append('\n')
                    .append("   ").append(entry.detail).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle(report.heading)
                .setMessage(message.toString().trim())
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void updateSessionCard() {
        if (sessionCard == null) {
            return;
        }
        boolean active = optimizer.hasActiveSession();
        sessionCard.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            String sessionLabel = optimizer.getSessionLabel();
            String profile = optimizer.getSessionProfile();
            boolean persistent = preferences.getBoolean(
                    Optimizer.KEY_BACKGROUND_SERVICE, true);
            StringBuilder detail = new StringBuilder(
                    profile == null ? "Perfil temporário" : profile);
            if (sessionLabel != null) {
                detail.append(" • ").append(sessionLabel);
            }
            if (preferences.contains(SessionHistory.KEY_CURRENT_TARGET_FPS)) {
                detail.append("\nFrameSense: alvo ")
                        .append(SessionHistory.currentTargetFps(preferences))
                        .append(" FPS");
                if (optimizer.wasThermalGuardApplied()) {
                    detail.append(" • proteção térmica ativa");
                }
            }
            detail.append(persistent
                    ? "\nMonitor persistente em execução."
                    : "\nRestaure ao terminar.");
            sessionDetail.setText(detail.toString());
        }
        updatePrimaryButtons();
    }

    private void updateDndAccess() {
        if (dndAccessButton == null) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean granted = manager != null && manager.isNotificationPolicyAccessGranted();
        dndAccessButton.setText(granted
                ? "ACESSO AO NÃO PERTURBE CONCEDIDO"
                : "CONCEDER ACESSO AO NÃO PERTURBE");
        dndAccessButton.setTextColor(granted ? UiKit.CYAN : UiKit.PURPLE);
    }

    private void updateBackgroundAccess() {
        if (usageAccessStatus == null || usageAccessAction == null
                || batteryProtectionStatus == null || batteryProtectionAction == null) {
            return;
        }

        boolean usageGranted = UsageAccess.isGranted(this);
        usageAccessStatus.setText(usageGranted
                ? "Ativo • o fim da partida será detectado localmente."
                : "Desativado • a restauração automática fica indisponível.");
        usageAccessStatus.setTextColor(usageGranted ? UiKit.CYAN : UiKit.YELLOW);
        usageAccessAction.setText(usageGranted ? "ATIVO" : "ATIVAR");
        usageAccessAction.setTextColor(usageGranted ? UiKit.CYAN : UiKit.PURPLE);
        usageAccessAction.setBackground(UiKit.rounded(
                usageGranted ? UiKit.GREEN_DARK : UiKit.PURPLE_DARK,
                UiKit.dp(this, 99), Color.TRANSPARENT, 0));

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean unrestricted = powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        batteryProtectionStatus.setText(unrestricted
                ? "O Android já liberou o app das otimizações de bateria."
                : "Recomendado para a Samsung não suspender o monitor.");
        batteryProtectionStatus.setTextColor(unrestricted ? UiKit.CYAN : UiKit.MUTED);
        batteryProtectionAction.setText(unrestricted ? "REVISAR" : "ABRIR");
    }

    private void updateHistoryCard() {
        if (historyCard == null) {
            return;
        }
        boolean available = SessionHistory.hasHistory(preferences);
        historyCard.setVisibility(available ? View.VISIBLE : View.GONE);
        if (available) {
            historyTitle.setText(SessionHistory.title(preferences));
            historyDetail.setText(SessionHistory.detail(preferences));
        }
    }

    private void openDoNotDisturbAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (Throwable error) {
            toast("Abra Configurações › Notificações › Não Perturbe");
        }
    }

    private void openUsageAccess() {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable firstError) {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Throwable ignored) {
                toast("Abra Configurações › Segurança › Acesso ao uso");
            }
        }
    }

    private void showBatteryProtectionGuide() {
        new AlertDialog.Builder(this)
                .setTitle("Impedir suspensão na Samsung")
                .setMessage("Na tela do PulseBoost, entre em Bateria e selecione Sem restrições. "
                        + "Se aparecer Limites de uso em segundo plano, não coloque o app em suspensão profunda.\n\n"
                        + "Isso não aumenta FPS; apenas reduz a chance de o monitor ser encerrado durante o jogo.")
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Abrir configurações", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Throwable error) {
                        toast("Abra Configurações › Aplicativos › PulseBoost › Bateria");
                    }
                })
                .show();
    }

    private void updatePrimaryButtons() {
        if (optimizeButton == null || prepareGameButton == null || restoreButton == null) {
            return;
        }
        boolean gameSelected = selectedPackage != null && !selectedPackage.trim().isEmpty();
        boolean ready = shizuku != null && shizuku.isReady();
        boolean activeSession = optimizer != null && optimizer.hasActiveSession();
        setActionEnabled(optimizeButton, !busy && gameSelected && ready && !activeSession);
        setActionEnabled(prepareGameButton, !busy && gameSelected && ready && !activeSession);
        setActionEnabled(restoreButton, !busy && optimizer != null && optimizer.hasActiveSession());
    }

    private void setBusy(boolean busy, String label) {
        this.busy = busy;
        optimizeButton.setText(busy && label != null ? label : "Otimizar e jogar");
        updatePrimaryButtons();
    }

    private static void setActionEnabled(TextView action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.43f);
    }

    private void showFirstRunGuide() {
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Antes da primeira partida")
                .setMessage("1. Inicie o Shizuku usando Depuração sem fio.\n"
                        + "2. Toque em Autorizar dentro do PulseBoost.\n"
                        + "3. Ative Acesso ao uso para detectar quando a partida acabar.\n"
                        + "4. Na bateria do app, escolha Sem restrições.\n"
                        + "5. Escolha o jogo e use Otimizar e jogar.\n\n"
                        + "O FrameSense recomenda um alvo estável e analisa os tempos dos quadros "
                        + "depois da partida. Ele não gera quadros nem altera o jogo.\n\n"
                        + "A notificação mantém a sessão visível e oferece o botão Restaurar.")
                .setPositiveButton("Entendi", null)
                .show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private final class GameListAdapter extends BaseAdapter {
        private final List<GameRepository.GameApp> all;
        private final List<GameRepository.GameApp> filtered = new ArrayList<>();

        GameListAdapter(List<GameRepository.GameApp> apps) {
            all = new ArrayList<>(apps);
            filtered.addAll(apps);
        }

        void filter(String query) {
            String normalized = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
            filtered.clear();
            if (normalized.isEmpty()) {
                filtered.addAll(all);
            } else {
                for (GameRepository.GameApp app : all) {
                    if (app.label.toLowerCase(Locale.getDefault()).contains(normalized)
                            || app.packageName.toLowerCase(Locale.US).contains(normalized)) {
                        filtered.add(app);
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return filtered.size(); }
        @Override public GameRepository.GameApp getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            GameRepository.GameApp app = getItem(position);
            LinearLayout row = UiKit.horizontal(MainActivity.this);
            row.setPadding(UiKit.dp(MainActivity.this, 7), UiKit.dp(MainActivity.this, 9),
                    UiKit.dp(MainActivity.this, 7), UiKit.dp(MainActivity.this, 9));
            row.setBackground(UiKit.ripple(UiKit.SURFACE, UiKit.dp(MainActivity.this, 13)));

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(icon, new LinearLayout.LayoutParams(
                    UiKit.dp(MainActivity.this, 44), UiKit.dp(MainActivity.this, 44)));

            LinearLayout copy = UiKit.vertical(MainActivity.this);
            TextView label = UiKit.text(MainActivity.this, app.label, 14.5f, UiKit.TEXT, true);
            TextView packageName = UiKit.text(MainActivity.this,
                    app.packageName, 10.5f, UiKit.MUTED, false);
            packageName.setSingleLine(true);
            copy.addView(label);
            copy.addView(UiKit.spacer(MainActivity.this, 4));
            copy.addView(packageName);
            LinearLayout.LayoutParams copyParams = UiKit.weight(1f);
            copyParams.setMargins(UiKit.dp(MainActivity.this, 12), 0,
                    UiKit.dp(MainActivity.this, 8), 0);
            row.addView(copy, copyParams);

            if (app.categorizedAsGame) {
                TextView tag = UiKit.pill(MainActivity.this,
                        "JOGO", UiKit.GREEN_DARK, UiKit.CYAN);
                row.addView(tag);
            }
            return row;
        }
    }
}
