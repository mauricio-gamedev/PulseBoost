package io.github.astromg01.pulseboost.shizuku;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class CommandService extends ICommandService.Stub {
    private static final int MAX_OUTPUT_BYTES = 128 * 1024;

    public CommandService() {
    }

    public CommandService(Context ignoredContext) {
    }

    @Override
    public String exec(String command) {
        if (command == null || command.trim().isEmpty() || command.length() > 4096) {
            return "-1\nComando inválido";
        }

        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream stream = process.getInputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            boolean truncated = false;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                int accepted = Math.min(read, MAX_OUTPUT_BYTES - total);
                if (accepted > 0) {
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
                if (accepted < read) {
                    truncated = true;
                }
            }

            int exitCode = process.waitFor();
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (truncated) {
                text += "\n[saída truncada com segurança]";
            }
            return exitCode + "\n" + text;
        } catch (Throwable error) {
            return "-1\n" + error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
