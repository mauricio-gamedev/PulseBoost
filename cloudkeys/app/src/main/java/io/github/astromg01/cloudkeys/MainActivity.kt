package io.github.astromg01.cloudkeys

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.astromg01.cloudkeys.overlay.OverlayService
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    companion object { private const val SHIZUKU_REQUEST = 1001 }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(36, 48, 36, 36)
            setBackgroundColor(Color.rgb(5, 7, 12))
        }

        val title = TextView(this).apply {
            text = "CloudKeys"
            textSize = 30f
            setTextColor(Color.WHITE)
        }

        val subtitle = TextView(this).apply {
            text = "PC shortcuts. Touch overlay. Cloud gaming."
            textSize = 15f
            setTextColor(Color.rgb(155, 166, 190))
        }

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(155, 166, 190))
            setPadding(0, 28, 0, 18)
        }

        val shizuku = Button(this).apply {
            text = "1. Autorizar Shizuku"
            setOnClickListener { requestShizuku() }
        }

        val overlay = Button(this).apply {
            text = "2. Ativar overlay [ I ]"
            setOnClickListener { startOverlay() }
        }

        val test = Button(this).apply {
            text = "3. Testar tecla I"
            setOnClickListener { OverlayService.injectKeyFromActivity(37) }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(shizuku)
        root.addView(overlay)
        root.addView(test)

        setContentView(root)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Abra o Shizuku e inicie o serviço.", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST)
        } else {
            Toast.makeText(this, "CloudKeys já está autorizado.", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!hasShizukuPermission()) {
            Toast.makeText(this, "Autorize o CloudKeys no Shizuku.", Toast.LENGTH_LONG).show()
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Overlay ativo.", Toast.LENGTH_SHORT).show()
    }

    private fun hasShizukuPermission(): Boolean =
        Shizuku.pingBinder() &&
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun updateStatus() {
        val shizukuState = when {
            !Shizuku.pingBinder() -> "Shizuku: não conectado"
            !hasShizukuPermission() -> "Shizuku: conectado, sem autorização"
            else -> "Shizuku: autorizado"
        }
        val overlayState = if (Settings.canDrawOverlays(this)) "Overlay: permitido" else "Overlay: precisa de permissão"
        status.text = "$shizukuState\n$overlayState\n\nMVP-0: botão [ I ] para teste no GeForce NOW."
    }
}
