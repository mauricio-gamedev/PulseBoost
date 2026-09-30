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
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    private static final String CHANNEL = "cloudkeys_overlay";
    private static final int NOTIFICATION_ID = 100;
    private WindowManager wm;
    private ShizukuKeyInjector injector;
    private final List<View> overlays = new ArrayList<>();
    private final List<TextView> keyViews = new ArrayList<>();
    private final List<WindowManager.LayoutParams> keyParams = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean editMode = false, locked = false;
    private float opacity = .78f, scale = 1f;
    private static OverlayService instance;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("cloudkeys", MODE_PRIVATE);
        opacity = prefs.getFloat("opacity", .78f);
        scale = prefs.getFloat("scale", 1f);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        injector = new ShizukuKeyInjector(this);
        addKeyboard();
        addEditorButton();
    }

    private void addKeyboard() {
        String[] labels = {"ESC","I","M","TAB","ENTER","SPACE","1","2","3","4","5","Q","W","E","R","A","S","D","F","G","Z","X","C","V"};
        int[] codes = {111,37,41,61,66,62,8,9,10,11,12,45,51,33,46,29,47,32,34,35,54,52,31,50};
        for (int i = 0; i < labels.length; i++) addKey(labels[i], codes[i], i);
    }

    private void addKey(final String label, final int keyCode, final int index) {
        final int size = Math.max(dp(34), (int)(dp(46) * scale));
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = dp(8) + (index % 6) * (size + dp(5));
        p.y = dp(105) + (index / 6) * (size + dp(5));

        TextView v = new TextView(this);
        v.setText(label);
        v.setTextSize(Math.max(11f, 14f * scale));
        v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE);
        v.setAlpha(opacity);
        v.setBackground(roundBackground());
        v.setClickable(false);
        v.setFocusable(false);
        v.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, startX, startY;
            boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX=e.getRawX(); downY=e.getRawY(); startX=p.x; startY=p.y; moved=false;
                        if (!editMode && injector != null) injector.sendKey(keyCode);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!editMode) return true;
                        float dx=e.getRawX()-downX, dy=e.getRawY()-downY;
                        if (Math.abs(dx)>dp(4) || Math.abs(dy)>dp(4)) moved=true;
                        if (moved) { p.x=(int)(startX+dx); p.y=(int)(startY+dy); try { wm.updateViewLayout(view,p); } catch(Throwable ignored) {} }
                        return true;
                    default: return true;
                }
            }
        });
        keyViews.add(v); keyParams.add(p); overlays.add(v);
        wm.addView(v,p);
    }

    private void addEditorButton() {
        final WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                dp(44),dp(44),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END; p.x=dp(10); p.y=dp(12);
        TextView v=new TextView(this);
        v.setText("⚙"); v.setTextSize(20); v.setGravity(Gravity.CENTER);
        v.setTextColor(Color.WHITE); v.setAlpha(.9f); v.setBackground(roundBackground());
        v.setOnClickListener(view -> showEditor());
        overlays.add(v); wm.addView(v,p);
    }

    private void showEditor() {
        final WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                dp(300),dp(250),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.CENTER;
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(8),dp(12),dp(8)); box.setBackground(roundBackground());
        TextView title=new TextView(this); title.setText("CloudKeys"); title.setTextColor(Color.WHITE); title.setTextSize(18); box.addView(title);

        Button edit=new Button(this); edit.setText("Editar / mover botões");
        edit.setOnClickListener(v -> { editMode=!editMode; locked=!editMode; }); box.addView(edit);

        Button lock=new Button(this); lock.setText(locked ? "Desfixar botões" : "Fixar botões");
        lock.setOnClickListener(v -> { locked=!locked; editMode=!locked; lock.setText(locked ? "Desfixar botões" : "Fixar botões"); }); box.addView(lock);

        TextView op=new TextView(this); op.setText("Opacidade"); op.setTextColor(Color.WHITE); box.addView(op);
        SeekBar opacityBar=new SeekBar(this); opacityBar.setMax(100); opacityBar.setProgress((int)(opacity*100));
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean fromUser) {
                opacity=Math.max(.25f,value/100f); for(TextView key:keyViews) key.setAlpha(opacity); prefs.edit().putFloat("opacity",opacity).apply();
            }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); box.addView(opacityBar);

        TextView sz=new TextView(this); sz.setText("Tamanho"); sz.setTextColor(Color.WHITE); box.addView(sz);
        SeekBar sizeBar=new SeekBar(this); sizeBar.setMax(150); sizeBar.setProgress((int)(scale*100));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b,int value,boolean fromUser) { scale=Math.max(.70f,Math.min(1.5f,value/100f)); resizeKeys(); prefs.edit().putFloat("scale",scale).apply(); }
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        }); box.addView(sizeBar);

        Button close=new Button(this); close.setText("Fechar"); close.setOnClickListener(v -> removeOverlay(box)); box.addView(close);
        overlays.add(box); wm.addView(box,p);
    }

    private void resizeKeys() {
        int size=Math.max(dp(34),(int)(dp(46)*scale));
        for(int i=0;i<keyViews.size();i++){ TextView v=keyViews.get(i); WindowManager.LayoutParams p=keyParams.get(i); p.width=size;p.height=size;v.setTextSize(Math.max(11f,14f*scale));try{wm.updateViewLayout(v,p);}catch(Throwable ignored){} }
    }

    private void removeOverlay(View v) { try{wm.removeView(v);}catch(Throwable ignored){} overlays.remove(v); }

    private GradientDrawable roundBackground() {
        GradientDrawable g=new GradientDrawable(); g.setCornerRadius(dp(10)); g.setColor(Color.argb(180,10,16,28)); g.setStroke(dp(1),Color.argb(150,90,170,255)); return g;
    }
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT>=26){ NotificationManager m=getSystemService(NotificationManager.class); m.createNotificationChannel(new NotificationChannel(CHANNEL,"CloudKeys overlay",NotificationManager.IMPORTANCE_LOW)); }
    }
    private Notification buildNotification() {
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("CloudKeys ativo").setContentText("Atalhos de teclado disponíveis.").setOngoing(true).build();
    }
    @Override public void onDestroy(){ for(View v:new ArrayList<>(overlays)){try{wm.removeView(v);}catch(Throwable ignored){}} overlays.clear();keyViews.clear();keyParams.clear();if(injector!=null){injector.close();injector=null;}instance=null;super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
