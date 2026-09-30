package io.github.astromg01.cloudkeys.overlay;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShizukuInputService extends IKeyInjector.Stub {
    // Two workers keep simultaneous taps responsive without spawning an unbounded
    // number of shell processes on low-RAM devices.
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void sendKey(int keyCode) {
        if (keyCode < 0 || keyCode > 288) {
            throw new IllegalArgumentException("Invalid Android key code: " + keyCode);
        }

        executor.execute(() -> inject(keyCode));
    }

    private void inject(int keyCode) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "/system/bin/input",
                    "keyevent",
                    String.valueOf(keyCode)
            ).redirectErrorStream(true).start();

            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[256];
                while (input.read(buffer) != -1) {
                    // Drain output so the process cannot block on its pipe.
                }
            }
            process.waitFor();
        } catch (Throwable ignored) {
            // Keep the overlay alive if a single injection fails.
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        System.exit(0);
    }
}
