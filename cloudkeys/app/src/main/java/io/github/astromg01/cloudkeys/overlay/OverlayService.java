package io.github.astromg01.cloudkeys.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    private static final String CHANNEL = "cloudkeys_overlay";
    private static final int NOTIFICATION_ID = 100;
    private static final String PREFS = "cloudkeys";

    private static final float DEFAULT_OPACITY = .78f;
    private static final float MIN_OPACITY = .25f;
    private static final float MIN_SCALE = .45f;
    private static final float MAX_SCALE = 2.50f;

    private static final float DEFAULT_CURSOR_SIZE = 40f;
    private static final float MIN_CURSOR_SIZE = 24f;
    private static final float MAX_CURSOR_SIZE = 72f;
    private static final float DEFAULT_CURSOR_SPEED = 1.0f;
    private static final float MIN_CURSOR_SPEED = .35f;
    private static final float MAX_CURSOR_SPEED = 3.0f;
    private static final float DEFAULT_CURSOR_OPACITY = .86f;

    private static final int DEFAULT_BUTTON_DP = 46;
    private static final int MIN_BUTTON_DP = 28;
    private static final int MAX_BUTTON_DP = 115;

    private static final String[] LABELS = {
            "ESC", "I", "M", "TAB", "ENTER", "SPACE",
            "1", "2", "3", "4", "5",
            "Q", "W", "E", "R",
            "A", "S", "D", "F", "G",
            "Z", "X", "C", "V"
    };

    private static final int[] CODES = {
            111, 37, 41, 61, 66, 62,
            8, 9, 10, 11, 12,
            45, 51, 33, 46,
            29, 47, 32, 34, 35,
            54, 52, 31, 50
    };

    private WindowManager wm;
    private ShizukuKeyInjector injector;
    private SharedPreferences prefs;
    private ProfileStore profileStore;
    private ForegroundDetector detector;

    private final List<View> overlays = new ArrayList<>();
    private final List<TextView> keyViews = new ArrayList<>();
    private final List<WindowManager.LayoutParams> keyParams = new ArrayList<>();

    private TextView editorView;
    private View editorOverlay;
    private VirtualCursorView cursorView;
    private WindowManager.LayoutParams cursorParams;

    private boolean editMode;
    private boolean locked;
    private boolean autoDetect;
    private boolean cursorEnabled;

    private float opacity;
    private float scale;
    private float cursorSize;
    private float cursorSpeed;
    private float cursorOpacity;

    private String activeProfilePackage;
    private boolean profileDirty;

    private static OverlayService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        profileStore = new ProfileStore(this);

        opacity = clampOpacity(
                prefs.getFloat("opacity", DEFAULT_OPACITY)
        );
        scale = clampScale(
                prefs.getFloat("scale", 1f)
        );
        cursorSize = clampCursorSize(
                prefs.getFloat("cursor_size", DEFAULT_CURSOR_SIZE)
        );
        cursorSpeed = clampCursorSpeed(
                prefs.getFloat("cursor_speed", DEFAULT_CURSOR_SPEED)
        );
        cursorOpacity = clampOpacity(
                prefs.getFloat(
                        "cursor_opacity",
                        DEFAULT_CURSOR_OPACITY
                )
        );
        cursorEnabled = prefs.getBoolean("cursor_enabled", true);
        autoDetect = prefs.getBoolean("auto_detect", true);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        injector = new ShizukuKeyInjector(this);

        addKeyboard();
        addCursor();
        addEditorButton();

        detector = new ForegroundDetector(
                this,
                getPackageName(),
                packageName -> switchProfile(packageName)
        );

        if (autoDetect) {
            detector.start();
        }
    }

    public static void injectKeyFromActivity(int keyCode) {
        OverlayService service = instance;
        if (service != null && service.injector != null) {
            service.injector.sendKey(keyCode);
        }
    }

    public static String getDetectedPackage() {
        OverlayService service = instance;
        return service == null ? null : service.activeProfilePackage;
    }

    public static boolean hasUsageAccess() {
        OverlayService service = instance;
        return service != null
                && ForegroundDetector.hasUsageAccess(service);
    }

    private void addKeyboard() {
        for (int i = 0; i < LABELS.length; i++) {
            addKey(LABELS[i], CODES[i], i);
        }
    }

    private void addKey(
            final String label,
            final int keyCode,
            final int index
    ) {
        final int size = buttonSize();

        final WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        size,
                        size,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );
        p.gravity = Gravity.TOP | Gravity.START;

        int defaultX = defaultKeyX(index, size);
        int defaultY = defaultKeyY(index, size);

        p.x = clampX(
                prefs.getInt("x_" + index, defaultX),
                size
        );
        p.y = clampY(
                prefs.getInt("y_" + index, defaultY),
                size
        );

        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(textSize());
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setAlpha(opacity);
        v.setBackground(roundBackground());
        v.setClickable(false);
        v.setFocusable(false);

        v.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            int startX;
            int startY;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = p.x;
                        startY = p.y;
                        moved = false;

                        if (!editMode && injector != null) {
                            injector.sendKey(keyCode);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (!editMode || locked) {
                            return true;
                        }

                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;

                        if (Math.abs(dx) > dp(4)
                                || Math.abs(dy) > dp(4)) {
                            moved = true;
                        }

                        if (moved) {
                            p.x = clampX(
                                    (int) (startX + dx),
                                    p.width
                            );
                            p.y = clampY(
                                    (int) (startY + dy),
                                    p.height
                            );
                            updateKeyLayout(view, p);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (editMode && !locked && moved) {
                            savePosition(index, p);
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });

        keyViews.add(v);
        keyParams.add(p);
        overlays.add(v);
        wm.addView(v, p);
    }

    private void addCursor() {
        final int sizePx = dp(Math.round(cursorSize));

        cursorParams = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        cursorParams.gravity = Gravity.TOP | Gravity.START;

        int defaultX =
                getResources().getDisplayMetrics().widthPixels / 2
                        - sizePx / 2;
        int defaultY =
                getResources().getDisplayMetrics().heightPixels / 2
                        - sizePx / 2;

        cursorParams.x = clampX(
                prefs.getInt("cursor_x", defaultX),
                sizePx
        );
        cursorParams.y = clampY(
                prefs.getInt("cursor_y", defaultY),
                sizePx
        );

        cursorView = new VirtualCursorView(
                this,
                cursorParams,
                cursorOpacity,
                cursorSpeed,
                new VirtualCursorView.Listener() {
                    @Override
                    public void onMove(int x, int y) {
                        cursorParams.x = clampX(x, cursorParams.width);
                        cursorParams.y = clampY(y, cursorParams.height);
                        updateKeyLayout(cursorView, cursorParams);
                        profileDirty = true;
                    }

                    @Override
                    public void onClick(int x, int y) {
                        if (injector != null) {
                            injector.sendTap(
                                    clampScreenX(x),
                                    clampScreenY(y)
                            );
                        }
                    }
                }
        );

        cursorView.setAlpha(cursorOpacity);
        cursorView.setVisibility(
                cursorEnabled ? View.VISIBLE : View.GONE
        );

        overlays.add(cursorView);
        wm.addView(cursorView, cursorParams);
    }

    private void addEditorButton() {
        final WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        dp(44),
                        dp(44),
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );
        p.gravity = Gravity.TOP | Gravity.END;
        p.x = dp(10);
        p.y = dp(12);

        TextView v = new TextView(this);
        v.setText("⚙");
        v.setTextSize(20);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setAlpha(.92f);
        v.setBackground(roundBackground());
        v.setOnClickListener(view -> showEditor());

        overlays.add(v);
        wm.addView(v, p);
    }

    private void showEditor() {
        if (editorOverlay != null) return;

        final WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        dp(340),
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT
                );
        p.gravity = Gravity.CENTER;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackground(roundBackground());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(10));

        TextView title = new TextView(this);
        title.setText("CloudKeys Universal");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        box.addView(title, matchWrap());

        TextView profileLabel = valueLabel(profileText());
        profileLabel.setTextSize(13);
        box.addView(profileLabel, matchWrap());

        Button detect = new Button(this);
        refreshDetectButton(detect);
        detect.setOnClickListener(v -> {
            if (!ForegroundDetector.hasUsageAccess(this)) {
                openUsageAccess();
                return;
            }

            autoDetect = !autoDetect;
            prefs.edit().putBoolean("auto_detect", autoDetect).apply();

            if (detector != null) {
                if (autoDetect) {
                    detector.start();
                } else {
                    detector.stop();
                }
            }
            refreshDetectButton(detect);
        });
        box.addView(detect, matchWrap());

        Button cursor = new Button(this);
        refreshCursorButton(cursor);
        cursor.setOnClickListener(v -> {
            cursorEnabled = !cursorEnabled;
            setCursorVisibility();
            profileDirty = true;
            refreshCursorButton(cursor);
        });
        box.addView(cursor, matchWrap());

        TextView cursorSizeLabel = valueLabel(
                "Cursor: " + Math.round(cursorSize) + "dp"
        );
        box.addView(cursorSizeLabel, matchWrap());

        SeekBar cursorSizeBar = new SeekBar(this);
        cursorSizeBar.setMax(100);
        cursorSizeBar.setProgress(
                Math.round(
                        ((cursorSize - MIN_CURSOR_SIZE)
                                / (MAX_CURSOR_SIZE - MIN_CURSOR_SIZE))
                                * 100f
                )
        );
        cursorSizeBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b,
                            int value,
                            boolean fromUser
                    ) {
                        cursorSize = MIN_CURSOR_SIZE
                                + (value / 100f)
                                * (MAX_CURSOR_SIZE
                                - MIN_CURSOR_SIZE);
                        cursorSizeLabel.setText(
                                "Cursor: "
                                        + Math.round(cursorSize)
                                        + "dp"
                        );
                        resizeCursor();
                        if (fromUser) {
                            profileDirty = true;
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(cursorSizeBar, matchWrap());

        TextView cursorSpeedLabel = valueLabel(
                "Velocidade do cursor: "
                        + String.format(
                        java.util.Locale.US,
                        "%.2fx",
                        cursorSpeed
                )
        );
        box.addView(cursorSpeedLabel, matchWrap());

        SeekBar cursorSpeedBar = new SeekBar(this);
        cursorSpeedBar.setMax(100);
        cursorSpeedBar.setProgress(
                Math.round(
                        ((cursorSpeed - MIN_CURSOR_SPEED)
                                / (MAX_CURSOR_SPEED
                                - MIN_CURSOR_SPEED))
                                * 100f
                )
        );
        cursorSpeedBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b,
                            int value,
                            boolean fromUser
                    ) {
                        cursorSpeed = MIN_CURSOR_SPEED
                                + (value / 100f)
                                * (MAX_CURSOR_SPEED
                                - MIN_CURSOR_SPEED);
                        cursorSpeedLabel.setText(
                                "Velocidade do cursor: "
                                        + String.format(
                                        java.util.Locale.US,
                                        "%.2fx",
                                        cursorSpeed
                                )
                        );
                        if (cursorView != null) {
                            cursorView.setSpeed(cursorSpeed);
                        }
                        if (fromUser) {
                            profileDirty = true;
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(cursorSpeedBar, matchWrap());

        TextView cursorOpacityLabel = valueLabel(
                "Opacidade do cursor: "
                        + Math.round(cursorOpacity * 100f)
                        + "%"
        );
        box.addView(cursorOpacityLabel, matchWrap());

        SeekBar cursorOpacityBar = new SeekBar(this);
        cursorOpacityBar.setMax(100);
        cursorOpacityBar.setProgress(
                Math.round(cursorOpacity * 100f)
        );
        cursorOpacityBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b,
                            int value,
                            boolean fromUser
                    ) {
                        cursorOpacity =
                                clampOpacity(value / 100f);
                        if (cursorView != null) {
                            cursorView.setAlpha(cursorOpacity);
                        }
                        cursorOpacityLabel.setText(
                                "Opacidade do cursor: "
                                        + Math.round(
                                        cursorOpacity * 100f
                                )
                                        + "%"
                        );
                        if (fromUser) {
                            profileDirty = true;
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(cursorOpacityBar, matchWrap());

        Button edit = new Button(this);
        edit.setAllCaps(false);
        edit.setText(
                editMode
                        ? "Concluir edição"
                        : "Editar / mover botões"
        );
        edit.setOnClickListener(v -> {
            if (locked) return;
            editMode = !editMode;
            edit.setText(
                    editMode
                            ? "Concluir edição"
                            : "Editar / mover botões"
            );
        });
        box.addView(edit, matchWrap());

        Button lock = new Button(this);
        lock.setAllCaps(false);
        lock.setText(
                locked ? "Desfixar botões" : "Fixar botões"
        );
        lock.setOnClickListener(v -> {
            locked = !locked;
            if (locked) editMode = false;
            lock.setText(
                    locked
                            ? "Desfixar botões"
                            : "Fixar botões"
            );
            edit.setText(
                    editMode
                            ? "Concluir edição"
                            : "Editar / mover botões"
            );
        });
        box.addView(lock, matchWrap());

        TextView opacityLabel = valueLabel(
                "Opacidade dos botões: "
                        + Math.round(opacity * 100f)
                        + "%"
        );
        box.addView(opacityLabel, matchWrap());

        SeekBar opacityBar = new SeekBar(this);
        opacityBar.setMax(100);
        opacityBar.setProgress(
                Math.round(opacity * 100f)
        );
        opacityBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b,
                            int value,
                            boolean fromUser
                    ) {
                        opacity = clampOpacity(value / 100f);
                        for (TextView key : keyViews) {
                            key.setAlpha(opacity);
                        }
                        opacityLabel.setText(
                                "Opacidade dos botões: "
                                        + Math.round(opacity * 100f)
                                        + "%"
                        );
                        if (fromUser) profileDirty = true;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(opacityBar, matchWrap());

        TextView sizeLabel = valueLabel(
                "Tamanho dos botões: "
                        + Math.round(scale * 100f)
                        + "%"
        );
        box.addView(sizeLabel, matchWrap());

        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(100);
        sizeBar.setProgress(scaleToProgress(scale));
        sizeBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b,
                            int value,
                            boolean fromUser
                    ) {
                        scale = progressToScale(value);
                        sizeLabel.setText(
                                "Tamanho dos botões: "
                                        + Math.round(scale * 100f)
                                        + "%"
                        );
                        resizeKeys();
                        if (fromUser) profileDirty = true;
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar b) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(sizeBar, matchWrap());

        Button reset = new Button(this);
        reset.setAllCaps(false);
        reset.setText("Resetar posições");
        reset.setOnClickListener(v -> {
            resetPositions();
            profileDirty = true;
        });
        box.addView(reset, matchWrap());

        Button close = new Button(this);
        close.setAllCaps(false);
        close.setText("Salvar e fechar");
        close.setOnClickListener(v -> {
            saveCurrentProfile();
            removeOverlay(scroll);
        });
        box.addView(close, matchWrap());

        scroll.addView(
                box,
                new ScrollView.LayoutParams(
                        ScrollView.LayoutParams.MATCH_PARENT,
                        ScrollView.LayoutParams.WRAP_CONTENT
                )
        );

        editorOverlay = scroll;
        overlays.add(scroll);
        wm.addView(scroll, p);
    }

    private void refreshDetectButton(Button button) {
        if (!ForegroundDetector.hasUsageAccess(this)) {
            button.setText("Permitir detecção automática");
        } else {
            button.setText(
                    "Detecção automática: "
                            + (autoDetect ? "ligada" : "desligada")
            );
        }
    }

    private void refreshCursorButton(Button button) {
        button.setText(
                "Cursor virtual: "
                        + (cursorEnabled ? "ligado" : "desligado")
        );
    }

    private void switchProfile(String packageName) {
        if (packageName == null
                || packageName.equals(activeProfilePackage)) {
            return;
        }

        saveCurrentProfile();

        activeProfilePackage = packageName;
        applyProfile(packageName);
        profileDirty = false;
    }

    private void applyProfile(String packageName) {
        boolean hasProfile =
                profileStore.hasProfile(packageName);

        float fallbackOpacity =
                clampOpacity(
                        prefs.getFloat(
                                "opacity",
                                DEFAULT_OPACITY
                        )
                );
        float fallbackScale =
                clampScale(
                        prefs.getFloat("scale", 1f)
                );

        opacity = clampOpacity(
                hasProfile
                        ? profileStore.getOpacity(
                        packageName,
                        fallbackOpacity
                )
                        : fallbackOpacity
        );
        scale = clampScale(
                hasProfile
                        ? profileStore.getScale(
                        packageName,
                        fallbackScale
                )
                        : fallbackScale
        );

        int size = buttonSize();

        for (int i = 0; i < keyViews.size(); i++) {
            WindowManager.LayoutParams p =
                    keyParams.get(i);

            int fallbackX = defaultKeyX(i, size);
            int fallbackY = defaultKeyY(i, size);

            if (!hasProfile) {
                fallbackX = prefs.getInt(
                        "x_" + i,
                        fallbackX
                );
                fallbackY = prefs.getInt(
                        "y_" + i,
                        fallbackY
                );
            } else {
                fallbackX = prefs.getInt(
                        "x_" + i,
                        fallbackX
                );
                fallbackY = prefs.getInt(
                        "y_" + i,
                        fallbackY
                );
            }

            p.width = size;
            p.height = size;
            p.x = clampX(
                    profileStore.getX(
                            packageName,
                            i,
                            fallbackX
                    ),
                    size
            );
            p.y = clampY(
                    profileStore.getY(
                            packageName,
                            i,
                            fallbackY
                    ),
                    size
            );

            keyViews.get(i).setTextSize(textSize());
            keyViews.get(i).setAlpha(opacity);
            updateKeyLayout(keyViews.get(i), p);
        }

        float globalCursorSize =
                clampCursorSize(
                        prefs.getFloat(
                                "cursor_size",
                                DEFAULT_CURSOR_SIZE
                        )
                );
        float globalCursorSpeed =
                clampCursorSpeed(
                        prefs.getFloat(
                                "cursor_speed",
                                DEFAULT_CURSOR_SPEED
                        )
                );
        float globalCursorOpacity =
                clampOpacity(
                        prefs.getFloat(
                                "cursor_opacity",
                                DEFAULT_CURSOR_OPACITY
                        )
                );
        boolean globalCursorEnabled =
                prefs.getBoolean("cursor_enabled", true);

        cursorSize = clampCursorSize(
                hasProfile
                        ? profileStore.getCursorSize(
                        packageName,
                        globalCursorSize
                )
                        : globalCursorSize
        );
        cursorSpeed = clampCursorSpeed(
                hasProfile
                        ? profileStore.getCursorSpeed(
                        packageName,
                        globalCursorSpeed
                )
                        : globalCursorSpeed
        );
        cursorOpacity = clampOpacity(
                hasProfile
                        ? profileStore.getCursorOpacity(
                        packageName,
                        globalCursorOpacity
                )
                        : globalCursorOpacity
        );
        cursorEnabled = hasProfile
                ? profileStore.getCursorEnabled(
                packageName,
                globalCursorEnabled
        )
                : globalCursorEnabled;

        resizeCursor();

        if (cursorView != null) {
            cursorView.setSpeed(cursorSpeed);
            cursorView.setAlpha(cursorOpacity);
        }
        setCursorVisibility();
    }

    private void resizeKeys() {
        int size = buttonSize();

        for (int i = 0; i < keyViews.size(); i++) {
            TextView v = keyViews.get(i);
            WindowManager.LayoutParams p =
                    keyParams.get(i);

            int centerX = p.x + p.width / 2;
            int centerY = p.y + p.height / 2;

            p.width = size;
            p.height = size;
            p.x = clampX(
                    centerX - size / 2,
                    size
            );
            p.y = clampY(
                    centerY - size / 2,
                    size
            );

            v.setTextSize(textSize());
            updateKeyLayout(v, p);
        }
    }

    private void resizeCursor() {
        if (cursorView == null
                || cursorParams == null) {
            return;
        }

        int size = dp(Math.round(cursorSize));
        int centerX =
                cursorParams.x + cursorParams.width / 2;
        int centerY =
                cursorParams.y + cursorParams.height / 2;

        cursorParams.width = size;
        cursorParams.height = size;
        cursorParams.x = clampX(
                centerX - size / 2,
                size
        );
        cursorParams.y = clampY(
                centerY - size / 2,
                size
        );

        updateKeyLayout(cursorView, cursorParams);
    }

    private void resetPositions() {
        int size = buttonSize();

        for (int i = 0; i < keyViews.size(); i++) {
            WindowManager.LayoutParams p =
                    keyParams.get(i);

            p.width = size;
            p.height = size;
            p.x = clampX(
                    defaultKeyX(i, size),
                    size
            );
            p.y = clampY(
                    defaultKeyY(i, size),
                    size
            );

            updateKeyLayout(keyViews.get(i), p);
        }

        if (cursorParams != null) {
            int cursorW = cursorParams.width;
            int cursorH = cursorParams.height;

            cursorParams.x = clampX(
                    getResources().getDisplayMetrics().widthPixels
                            / 2 - cursorW / 2,
                    cursorW
            );
            cursorParams.y = clampY(
                    getResources().getDisplayMetrics().heightPixels
                            / 2 - cursorH / 2,
                    cursorH
            );
            updateKeyLayout(cursorView, cursorParams);
        }
    }

    private void setCursorVisibility() {
        if (cursorView == null) return;
        cursorView.setVisibility(
                cursorEnabled
                        ? View.VISIBLE
                        : View.GONE
        );
        cursorView.setAlpha(cursorOpacity);
    }

    private void updateKeyLayout(
            View view,
            WindowManager.LayoutParams p
    ) {
        try {
            if (wm != null && view != null) {
                wm.updateViewLayout(view, p);
            }
        } catch (Throwable ignored) {
        }
    }

    private void savePosition(
            int index,
            WindowManager.LayoutParams p
    ) {
        if (activeProfilePackage != null) {
            profileStore.savePosition(
                    activeProfilePackage,
                    index,
                    p.x,
                    p.y
            );
        } else {
            prefs.edit()
                    .putInt("x_" + index, p.x)
                    .putInt("y_" + index, p.y)
                    .apply();
        }
        profileDirty = true;
    }

    private void saveCurrentProfile() {
        if (!profileDirty) return;

        if (activeProfilePackage == null) {
            SharedPreferences.Editor editor = prefs.edit()
                    .putFloat("opacity", opacity)
                    .putFloat("scale", scale)
                    .putFloat("cursor_size", cursorSize)
                    .putFloat("cursor_speed", cursorSpeed)
                    .putFloat("cursor_opacity", cursorOpacity)
                    .putBoolean("cursor_enabled", cursorEnabled);

            for (int i = 0; i < keyParams.size(); i++) {
                WindowManager.LayoutParams p =
                        keyParams.get(i);
                editor.putInt("x_" + i, p.x);
                editor.putInt("y_" + i, p.y);
            }

            if (cursorParams != null) {
                editor.putInt("cursor_x", cursorParams.x);
                editor.putInt("cursor_y", cursorParams.y);
            }

            editor.apply();
            profileDirty = false;
            return;
        }

        profileStore.saveAppearance(
                activeProfilePackage,
                opacity,
                scale
        );

        for (int i = 0; i < keyParams.size(); i++) {
            WindowManager.LayoutParams p =
                    keyParams.get(i);
            profileStore.savePosition(
                    activeProfilePackage,
                    i,
                    p.x,
                    p.y
            );
        }

        if (cursorParams != null) {
            profileStore.saveCursor(
                    activeProfilePackage,
                    cursorParams.x,
                    cursorParams.y,
                    cursorSize,
                    cursorSpeed,
                    cursorOpacity,
                    cursorEnabled
            );
        }

        profileDirty = false;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private TextView valueLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        return t;
    }

    private String profileText() {
        if (activeProfilePackage == null) {
            return "Perfil: padrão global";
        }
        return "Perfil automático: "
                + activeProfilePackage;
    }

    private int defaultKeyX(int index, int size) {
        return dp(8)
                + (index % 6) * (size + dp(5));
    }

    private int defaultKeyY(int index, int size) {
        return dp(96)
                + (index / 6) * (size + dp(5));
    }

    private int buttonSize() {
        return Math.max(
                dp(MIN_BUTTON_DP),
                Math.min(
                        dp(MAX_BUTTON_DP),
                        Math.round(
                                dp(DEFAULT_BUTTON_DP)
                                        * scale
                        )
                )
        );
    }

    private int textSize() {
        return Math.round(
                Math.max(
                        9f,
                        Math.min(
                                28f,
                                14f * scale
                        )
                )
        );
    }

    private float clampOpacity(float value) {
        return Math.max(
                MIN_OPACITY,
                Math.min(1f, value)
        );
    }

    private float clampScale(float value) {
        return Math.max(
                MIN_SCALE,
                Math.min(MAX_SCALE, value)
        );
    }

    private float clampCursorSize(float value) {
        return Math.max(
                MIN_CURSOR_SIZE,
                Math.min(MAX_CURSOR_SIZE, value)
        );
    }

    private float clampCursorSpeed(float value) {
        return Math.max(
                MIN_CURSOR_SPEED,
                Math.min(MAX_CURSOR_SPEED, value)
        );
    }

    private int scaleToProgress(float value) {
        float clamped = clampScale(value);
        return Math.round(
                ((clamped - MIN_SCALE)
                        / (MAX_SCALE - MIN_SCALE))
                        * 100f
        );
    }

    private float progressToScale(int progress) {
        float p = Math.max(
                0f,
                Math.min(100f, progress)
        ) / 100f;

        return MIN_SCALE
                + p * (MAX_SCALE - MIN_SCALE);
    }

    private int clampScreenX(int value) {
        int width =
                getResources().getDisplayMetrics().widthPixels;
        return Math.max(
                0,
                Math.min(value, Math.max(0, width - 1))
        );
    }

    private int clampScreenY(int value) {
        int height =
                getResources().getDisplayMetrics().heightPixels;
        return Math.max(
                0,
                Math.min(value, Math.max(0, height - 1))
        );
    }

    private int clampX(int value, int width) {
        int screenWidth =
                getResources().getDisplayMetrics().widthPixels;
        return Math.max(
                0,
                Math.min(
                        value,
                        Math.max(
                                0,
                                screenWidth - width
                        )
                )
        );
    }

    private int clampY(int value, int height) {
        int screenHeight =
                getResources().getDisplayMetrics().heightPixels;
        return Math.max(
                0,
                Math.min(
                        value,
                        Math.max(
                                0,
                                screenHeight - height
                        )
                )
        );
    }

    private GradientDrawable roundBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(Color.argb(180, 10, 16, 28));
        g.setStroke(
                dp(1),
                Color.argb(150, 90, 170, 255)
        );
        return g;
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + .5f
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager m =
                    getSystemService(
                            NotificationManager.class
                    );

            m.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL,
                            "CloudKeys overlay",
                            NotificationManager.IMPORTANCE_LOW
                    )
            );
        }
    }

    private Notification buildNotification() {
        Notification.Builder b =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(
                        this,
                        CHANNEL
                )
                        : new Notification.Builder(this);

        return b.setSmallIcon(
                        android.R.drawable.ic_menu_manage
                )
                .setContentTitle(
                        "CloudKeys Universal ativo"
                )
                .setContentText(
                        "Overlay para jogos e apps Android."
                )
                .setOngoing(true)
                .build();
    }

    private void openUsageAccess() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS
            );
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
            );
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void removeOverlay(View v) {
        try {
            if (wm != null) {
                wm.removeView(v);
            }
        } catch (Throwable ignored) {
        }

        overlays.remove(v);

        if (v == editorOverlay) {
            editorOverlay = null;
        }
    }

    @Override
    public void onDestroy() {
        saveCurrentProfile();

        if (detector != null) {
            detector.stop();
            detector = null;
        }

        for (View v : new ArrayList<>(overlays)) {
            try {
                if (wm != null) {
                    wm.removeView(v);
                }
            } catch (Throwable ignored) {
            }
        }

        overlays.clear();
        keyViews.clear();
        keyParams.clear();
        editorView = null;
        editorOverlay = null;
        cursorView = null;
        cursorParams = null;

        if (injector != null) {
            injector.close();
            injector = null;
        }

        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
