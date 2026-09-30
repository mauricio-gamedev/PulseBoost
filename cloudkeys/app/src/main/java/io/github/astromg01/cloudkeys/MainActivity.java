package io.github.astromg01.cloudkeys;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.astromg01.cloudkeys.overlay.OverlayService;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int SHIZUKU_REQUEST = 1001;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(36, 48, 36, 36);
        root.setBackgroundColor(Color.rgb(5, 7, 12));

        TextView title = new TextView(this);
        title.setText("CloudKeys");
        title.setTextSize(30f);
        title.setTextColor(Color.WHITE);

        TextView subtitle = new TextView(this);
        subtitle.setText("PC shortcuts. Touch overlay. Cloud gaming.");
        subtitle.setTextSize(15f);
        subtitle.setTextColor(Color.rgb(155, 166, 190));

        status = new TextView(this);
        status.setTextSize(14f);
        status.setTextColor(Color.rgb(155, 166, 190));
        status.setPadding(0, 28, 0, 18);

        Button shizuku = new Button(this);
        shizuku.setText("1. Autorizar Shizuku");
        shizuku.setOnClickListener(v -> requestShizuku());

        Button overlay = new Button(this);
        overlay.setText("2. Ativar overlay");
        overlay.setOnClickListener(v -> startOverlay());

        Button stop = new Button(this);
        stop.setText("3. Parar overlay");
        stop.setOnClickListener(v -> stopOverlay());

        Button test = new Button(this);
        test.setText("4. Testar tecla I");
        test.setOnClickListener(v -> {
            if (!hasShizukuPermission()) {
                Toast.makeText(
                        this,
                        "Autorize o CloudKeys no Shizuku primeiro.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            OverlayService.injectKeyFromActivity(37);
            Toast.makeText(this, "Tecla I enviada.", Toast.LENGTH_SHORT).show();
        });

        root.addView(title);
        root.addView(subtitle);
        root.addView(status);
        root.addView(shizuku);
        root.addView(overlay);
        root.addView(stop);
        root.addView(test);

        setContentView(root);
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) {
            updateStatus();
        }
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(
                    this,
                    "Abra o Shizuku e inicie o serviço.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (Shizuku.checkSelfPermission()
                != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST);
        } else {
            Toast.makeText(
                    this,
                    "CloudKeys já está autorizado.",
                    Toast.LENGTH_SHORT
            ).show();
        }
        updateStatus();
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            ));
            return;
        }

        if (!hasShizukuPermission()) {
            Toast.makeText(
                    this,
                    "Autorize o CloudKeys no Shizuku.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try {
            startForegroundService(
                    new Intent(this, OverlayService.class)
            );
            Toast.makeText(
                    this,
                    "Overlay ativo.",
                    Toast.LENGTH_SHORT
            ).show();
        } catch (Throwable t) {
            Toast.makeText(
                    this,
                    "Não foi possível iniciar o overlay.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void stopOverlay() {
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "Overlay parado.", Toast.LENGTH_SHORT).show();
    }

    private boolean hasShizukuPermission() {
        return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission()
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateStatus() {
        String shizukuState;
        if (!Shizuku.pingBinder()) {
            shizukuState = "Shizuku: não conectado";
        } else if (!hasShizukuPermission()) {
            shizukuState = "Shizuku: conectado, sem autorização";
        } else {
            shizukuState = "Shizuku: autorizado";
        }

        String overlayState = Settings.canDrawOverlays(this)
                ? "Overlay: permitido"
                : "Overlay: precisa de permissão";

        status.setText(
                shizukuState
                        + "\n"
                        + overlayState
                        + "\n\nCloudKeys MVP-1: atalhos + editor + tamanho + posição."
        );
    }
}
