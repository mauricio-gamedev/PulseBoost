package io.github.astromg01.cloudkeys.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
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
import android.widget.TextView;

public class OverlayService extends Service {

    private static final String CHANNEL = "cloudkeys_overlay";
    private static final int NOTIFICATION_ID = 100;

    private static OverlayService instance;
    private WindowManager windowManager;
    private final java.util.List<TextView> keyViews = new java.util.ArrayList<>();
    private final java.util.List<WindowManager.LayoutParams> keyParams = new java.util.ArrayList<>();
    private ShizukuKeyInjector injector;
    private boolean editMode=false, locked=false;
    private float opacity=.78f, scale=1f;

    public static void injectKeyFromActivity(int keyCode) {
        OverlayService current = instance;
        if (current != null && current.injector != null) {
            current.injector.sendKey(keyCode);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        injector = new ShizukuKeyInjector(this);

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        addKeyboard();
    addEditorButton();
    }

    private void addInventoryButton() {
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(60),
                dp(52),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(14);
        params.y = dp(220);

        TextView v = new TextView(this);
        v.setText("I");
        v.setTextSize(18f);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(Color.argb(178, 11, 18, 32));
        background.setStroke(dp(1), Color.argb(160, 90, 170, 255));
        v.setBackground(background);
        v.setClickable(true);

        v.setOnClickListener(view -> {
            if (injector != null) {
                injector.sendKey(37);
            }
        });

        v.setOnTouchListener(new View.OnTouchListener() {
            private int downX;
            private int downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = (int) event.getRawX();
                        downY = (int) event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) event.getRawX() - downX;
                        int dy = (int) event.getRawY() - downY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) {
                            moved = true;
                        }
                        if (moved) {
                            params.x = startX - dx;
                            params.y = startY + dy;
                            windowManager.updateViewLayout(view, params);
                            return true;
                        }
                        return false;

                    default:
                        return false;
                }
            }
        });

        button = v;
        windowManager.addView(v, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL,
                    "CloudKeys overlay",
                    NotificationManager.IMPORTANCE_LOW
            ));
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setContentTitle("CloudKeys ativo")
                    .setContentText("Atalho [ I ] disponível.")
                    .setOngoing(true)
                    .build();
        }

        return new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentTitle("CloudKeys ativo")
                .setContentText("Atalho [ I ] disponível.")
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        for(TextView v:keyViews)try{wm.removeView(v);}catch(Throwable ignored){}
        keyViews.clear(); keyParams.clear();

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
