package io.github.astromg01.pulseboost;

final class ShellResult {
    final int exitCode;
    final String output;

    ShellResult(int exitCode, String output) {
        this.exitCode = exitCode;
        this.output = output == null ? "" : output.trim();
    }

    boolean isSuccess() {
        return exitCode == 0;
    }

    static ShellResult parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new ShellResult(-1, "Resposta vazia do Shizuku");
        }
        int separator = raw.indexOf('\n');
        String codeText = separator >= 0 ? raw.substring(0, separator).trim() : raw.trim();
        String output = separator >= 0 ? raw.substring(separator + 1) : "";
        try {
            return new ShellResult(Integer.parseInt(codeText), output);
        } catch (NumberFormatException error) {
            return new ShellResult(-1, raw);
        }
    }
}
