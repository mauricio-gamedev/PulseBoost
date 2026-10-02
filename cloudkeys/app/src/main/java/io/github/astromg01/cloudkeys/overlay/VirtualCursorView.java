package io.github.astromg01.cloudkeys.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public final class VirtualCursorView extends View {
    public interface Listener {
        void onMove(int x, int y);

        void onClick(int x, int y);
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Listener listener;

    private WindowManager.LayoutParams params;
    private float speed = 1f;
    private float lastRawX;
    private float lastRawY;
    private boolean moved;

    public VirtualCursorView(
            Context context,
            WindowManager.LayoutParams params,
            float opacity,
            float speed,
            Listener listener
    ) {
        super(context);
        this.params = params;
        this.speed = speed;
        this.listener = listener;

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setAlpha(opacity);
        setClickable(true);
        setFocusable(false);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(0xFFFFFFFF);

        corePaint.setStyle(Paint.Style.FILL);
        corePaint.setColor(0xCCFFFFFF);
    }

    public void setParams(WindowManager.LayoutParams params) {
        this.params = params;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(.35f, Math.min(3f, speed));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() * .5f;
        float cy = getHeight() * .5f;
        float radius = Math.max(6f, Math.min(getWidth(), getHeight()) * .30f);

        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, Math.max(2f, radius * .22f), corePaint);

        float tick = Math.max(4f, radius * .50f);
        canvas.drawLine(
                cx,
                cy - radius - tick * .15f,
                cx,
                cy - radius + tick,
                ringPaint
        );
        canvas.drawLine(
                cx - radius - tick * .15f,
                cy,
                cx - radius + tick,
                cy,
                ringPaint
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moved = false;
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - lastRawX;
                float dy = event.getRawY() - lastRawY;

                if (Math.abs(dx) > 0.2f || Math.abs(dy) > 0.2f) {
                    moved = true;
                }

                if (params != null && moved) {
                    params.x += Math.round(dx * speed);
                    params.y += Math.round(dy * speed);
                    listener.onMove(params.x, params.y);
                }

                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved && params != null) {
                    listener.onClick(
                            params.x + getWidth() / 2,
                            params.y + getHeight() / 2
                    );
                }
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }
}
