package io.github.astromg01.cloudkeys.overlay;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShizukuInputService extends IKeyInjector.Stub {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void sendKey(int keyCode) {
        if (keyCode < 0 || keyCode > 288) {
            throw new IllegalArgumentException("Invalid Android key code: " + keyCode);
        }

        executor.execute(() -> {
            Process process = null;
            try {
                process = new ProcessBuilder(
                        "/system/bin/input",
                        "keyevent",
                        String.valueOf(keyCode)
                ).redirectErrorStream(true).start();

                try (InputStream input = process.getInputStream()) {
                    byte[] buffer = new byte[512];
                    while (input.read(buffer) != -1) {
                        // Drain output so the process cannot block on its pipe.
                    }
                }

                process.waitFor();
            } catch (Throwable ignored) {
                // MVP intentionally fails silently; a later build will expose status.
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        System.exit(0);
    }
}
