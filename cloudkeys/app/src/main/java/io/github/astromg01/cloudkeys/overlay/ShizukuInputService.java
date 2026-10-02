package io.github.astromg01.cloudkeys.overlay;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ShizukuInputService extends IKeyInjector.Stub {
    // Keep a tiny bounded worker pool so cursor taps and keyboard shortcuts
    // stay responsive without spawning an unbounded number of processes.
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Override
    public void sendKey(int keyCode) {
        if (keyCode < 0 || keyCode > 288) {
            throw new IllegalArgumentException("Invalid Android key code: " + keyCode);
        }

        executor.execute(() -> runInput(
                "keyevent",
                String.valueOf(keyCode)
        ));
    }

    @Override
    public void sendTap(int x, int y) {
        if (x < 0 || y < 0 || x > 10000 || y > 10000) {
            throw new IllegalArgumentException("Invalid tap coordinates");
        }

        executor.execute(() -> runInput(
                "tap",
                String.valueOf(x),
                String.valueOf(y)
        ));
    }

    private void runInput(String... args) {
        Process process = null;
        try {
            String[] command = new String[args.length + 2];
            command[0] = "/system/bin/input";
            command[1] = args[0];
            System.arraycopy(args, 1, command, 2, args.length - 1);

            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[256];
                while (input.read(buffer) != -1) {
                    // Drain output so the process cannot block on its pipe.
                }
            }

            process.waitFor();
        } catch (Throwable ignored) {
            // A failed injection must never bring down the overlay.
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
        System.exit(0);
    }
}
