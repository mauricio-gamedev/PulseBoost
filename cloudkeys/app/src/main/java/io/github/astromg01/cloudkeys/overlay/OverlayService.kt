package io.github.astromg01.cloudkeys.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.github.astromg01.cloudkeys.R

class OverlayService : Service() {

    companion object {
        private const val CHANNEL = "cloudkeys_overlay"
        private const val NOTIFICATION_ID = 100
        private var instance: OverlayService? = null

        fun injectKeyFromActivity(keyCode: Int) {
            instance?.injector?.sendKey(keyCode)
        }
    }

    private lateinit var windowManager: WindowManager
    private var button: TextView? = null
    private lateinit var injector: ShizukuKeyInjector

    override fun onCreate() {
        super.onCreate()
        instance = this
        injector = ShizukuKeyInjector(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addInventoryButton()
    }

    private fun addInventoryButton() {
        val params = WindowManager.LayoutParams(
            dp(60),
            dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(14)
            y = dp(220)
        }

        val v = TextView(this).apply {
            text = "I"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(178, 11, 18, 32))
                setStroke(dp(1), Color.argb(160, 90, 170, 255))
            }
            isClickable = true
            setOnClickListener { injector.sendKey(37) } // Android KEYCODE_I
        }

        v.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0
            var downY = 0
            var startX = 0
            var startY = 0
            var moved = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX.toInt()
                        downY = event.rawY.toInt()
                        startX = params.x
                        startY = params.y
                        moved = false
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX.toInt() - downX
                        val dy = event.rawY.toInt() - downY
                        if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) moved = true
                        if (moved) {
                            params.x = startX - dx
                            params.y = startY + dy
                            windowManager.updateViewLayout(view, params)
                            return true
                        }
                    }
                }
                return false
            }
        })

        button = v
        windowManager.addView(v, params)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "CloudKeys overlay", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("CloudKeys ativo")
            .setContentText("Atalho [ I ] disponível.")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        button?.let { runCatching { windowManager.removeView(it) } }
        button = null
        injector.close()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
