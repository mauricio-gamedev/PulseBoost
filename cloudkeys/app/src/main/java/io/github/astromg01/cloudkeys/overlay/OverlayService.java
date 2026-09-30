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

    private final List<View> overlays = new ArrayList<>();
    private final List<TextView> keyViews = new ArrayList<>();
    private final List<WindowManager.LayoutParams> keyParams = new ArrayList<>();

    private boolean editMode;
    private boolean locked;
    private float opacity;
    private float scale;

    private static OverlayService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        opacity = clampOpacity(prefs.getFloat("opacity", DEFAULT_OPACITY));
        scale = clampScale(prefs.getFloat("scale", 1f));

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        injector = new ShizukuKeyInjector(this);

        addKeyboard();
        addEditorButton();
    }

    public static void injectKeyFromActivity(int keyCode) {
        OverlayService service = instance;
        if (service != null && service.injector != null) {
            service.injector.sendKey(keyCode);
        }
    }

    private void addKeyboard() {
        for (int i = 0; i < LABELS.length; i++) {
            addKey(LABELS[i], CODES[i], i);
        }
    }

    private void addKey(final String label, final int keyCode, final int index) {
        final int size = buttonSize();

        final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        p.gravity = Gravity.TOP | Gravity.START;

        int defaultX = dp(8) + (index % 6) * (size + dp(5));
        int defaultY = dp(96) + (index / 6) * (size + dp(5));

        p.x = clampX(prefs.getInt("x_" + index, defaultX), size);
        p.y = clampY(prefs.getInt("y_" + index, defaultY), size);

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

                        // The button must stay usable while locked. Lock only
                        // prevents moving/editing it.
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

                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                            moved = true;
                        }

                        if (moved) {
                            p.x = clampX((int) (startX + dx), p.width);
                            p.y = clampY((int) (startY + dy), p.height);
                            updateKeyLayout(view, p);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Save once, instead of writing preferences on every
                        // movement frame.
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

    private void addEditorButton() {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
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
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                dp(330),
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
        title.setText("CloudKeys");
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        box.addView(title, matchWrap());

        Button edit = new Button(this);
        edit.setAllCaps(false);
        edit.setText(editMode ? "Concluir edição" : "Editar / mover botões");
        edit.setOnClickListener(v -> {
            if (locked) {
                return;
            }
            editMode = !editMode;
            edit.setText(editMode ? "Concluir edição" : "Editar / mover botões");
        });
        box.addView(edit, matchWrap());

        Button lock = new Button(this);
        lock.setAllCaps(false);
        lock.setText(locked ? "Desfixar botões" : "Fixar botões");
        lock.setOnClickListener(v -> {
            locked = !locked;
            if (locked) {
                editMode = false;
            }
            lock.setText(locked ? "Desfixar botões" : "Fixar botões");
            edit.setText(editMode ? "Concluir edição" : "Editar / mover botões");
        });
        box.addView(lock, matchWrap());

        TextView opacityLabel = valueLabel(
                "Opacidade: " + Math.round(opacity * 100f) + "%"
        );
        box.addView(opacityLabel, matchWrap());

        SeekBar opacityBar = new SeekBar(this);
        opacityBar.setMax(100);
        opacityBar.setProgress(Math.round(opacity * 100f));
        opacityBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b, int value, boolean fromUser
                    ) {
                        opacity = clampOpacity(value / 100f);
                        for (TextView key : keyViews) {
                            key.setAlpha(opacity);
                        }
                        opacityLabel.setText(
                                "Opacidade: " + Math.round(opacity * 100f) + "%"
                        );
                        if (fromUser) {
                            prefs.edit().putFloat("opacity", opacity).apply();
                        }
                    }

                    @Override public void onStartTrackingTouch(SeekBar b) {}
                    @Override public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(opacityBar, matchWrap());

        TextView sizeLabel = valueLabel(
                "Tamanho: " + Math.round(scale * 100f) + "%"
        );
        box.addView(sizeLabel, matchWrap());

        // Use a 0..100 progress range instead of SeekBar#setMin(), keeping
        // the app compatible with the declared minSdk while still exposing
        // the full 45%..250% scale range.
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(100);
        sizeBar.setProgress(scaleToProgress(scale));
        sizeBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar b, int value, boolean fromUser
                    ) {
                        scale = progressToScale(value);
                        sizeLabel.setText(
                                "Tamanho: " + Math.round(scale * 100f) + "%"
                        );
                        resizeKeys();
                        if (fromUser) {
                            prefs.edit().putFloat("scale", scale).apply();
                        }
                    }

                    @Override public void onStartTrackingTouch(SeekBar b) {}
                    @Override public void onStopTrackingTouch(SeekBar b) {}
                }
        );
        box.addView(sizeBar, matchWrap());

        Button reset = new Button(this);
        reset.setAllCaps(false);
        reset.setText("Resetar posições");
        reset.setOnClickListener(v -> resetPositions());
        box.addView(reset, matchWrap());

        Button close = new Button(this);
        close.setAllCaps(false);
        close.setText("Fechar");
        close.setOnClickListener(v -> removeOverlay(scroll));
        box.addView(close, matchWrap());

        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        overlays.add(scroll);
        wm.addView(scroll, p);
    }

    private void resizeKeys() {
        int size = buttonSize();

        for (int i = 0; i < keyViews.size(); i++) {
            TextView v = keyViews.get(i);
            WindowManager.LayoutParams p = keyParams.get(i);

            int centerX = p.x + p.width / 2;
            int centerY = p.y + p.height / 2;

            p.width = size;
            p.height = size;
            p.x = clampX(centerX - size / 2, size);
            p.y = clampY(centerY - size / 2, size);

            v.setTextSize(textSize());
            updateKeyLayout(v, p);
        }
    }

    private void resetPositions() {
        int size = buttonSize();

        for (int i = 0; i < keyViews.size(); i++) {
            WindowManager.LayoutParams p = keyParams.get(i);

            p.width = size;
            p.height = size;
            p.x = clampX(dp(8) + (i % 6) * (size + dp(5)), size);
            p.y = clampY(dp(96) + (i / 6) * (size + dp(5)), size);

            updateKeyLayout(keyViews.get(i), p);
            savePosition(i, p);
        }
    }

    private void updateKeyLayout(View view, WindowManager.LayoutParams p) {
        try {
            wm.updateViewLayout(view, p);
        } catch (Throwable ignored) {
        }
    }

    private void savePosition(int index, WindowManager.LayoutParams p) {
        prefs.edit()
                .putInt("x_" + index, p.x)
                .putInt("y_" + index, p.y)
                .apply();
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

    private int buttonSize() {
        return Math.max(
                dp(MIN_BUTTON_DP),
                Math.min(
                        dp(MAX_BUTTON_DP),
                        Math.round(dp(DEFAULT_BUTTON_DP) * scale)
                )
        );
    }

    private int textSize() {
        return Math.round(
                Math.max(9f, Math.min(28f, 14f * scale))
        );
    }

    private float clampOpacity(float value) {
        return Math.max(MIN_OPACITY, Math.min(1f, value));
    }

    private float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    private int scaleToProgress(float value) {
        float clamped = clampScale(value);
        return Math.round(
                ((clamped - MIN_SCALE) / (MAX_SCALE - MIN_SCALE)) * 100f
        );
    }

    private float progressToScale(int progress) {
        float p = Math.max(0f, Math.min(100f, progress / 100f));
        return MIN_SCALE + p * (MAX_SCALE - MIN_SCALE);
    }

    private int clampX(int value, int width) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(
                0,
                Math.min(value, Math.max(0, screenWidth - width))
        );
    }

    private int clampY(int value, int height) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        return Math.max(
                0,
                Math.min(value, Math.max(0, screenHeight - height))
        );
    }

    private GradientDrawable roundBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(Color.argb(180, 10, 16, 28));
        g.setStroke(dp(1), Color.argb(150, 90, 170, 255));
        return g;
    }

    private int dp(int value) {
        return (int) (
                value * getResources().getDisplayMetrics().density + .5f
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager m =
                    getSystemService(NotificationManager.class);

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
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);

        return b.setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("CloudKeys ativo")
                .setContentText("Atalhos de teclado disponíveis.")
                .setOngoing(true)
                .build();
    }

    private void removeOverlay(View v) {
        try {
            if (wm != null) {
                wm.removeView(v);
            }
        } catch (Throwable ignored) {
        }
        overlays.remove(v);
    }

    @Override
    public void onDestroy() {
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
