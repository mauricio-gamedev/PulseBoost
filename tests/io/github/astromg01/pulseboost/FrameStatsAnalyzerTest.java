package io.github.astromg01.pulseboost;

public final class FrameStatsAnalyzerTest {
    private static final String SAMPLE =
            "---PROFILEDATA---\n"
                    + "Flags,IntendedVsync,FrameCompleted\n"
                    + "0,1000000000,1016000000\n"
                    + "0,2000000000,2035000000\n"
                    + "1,3000000000,3900000000\n"
                    + "---PROFILEDATA---\n";

    public static void main(String[] args) {
        FrameStatsAnalyzer.Result sixty = FrameStatsAnalyzer.parse(SAMPLE, 60);
        require(sixty.available, "a amostra deveria estar disponível");
        require(sixty.totalFrames == 2, "flags inválidas devem ser ignoradas");
        require(sixty.unstableFrames == 1, "35 ms é instável para alvo de 60 FPS");
        require(sixty.stabilityPercent == 50, "estabilidade esperada de 50%");
        require(Math.abs(sixty.medianMs - 16d) < 0.01d, "mediana incorreta");
        require(Math.abs(sixty.percentile95Ms - 35d) < 0.01d, "p95 incorreto");

        FrameStatsAnalyzer.Result thirty = FrameStatsAnalyzer.parse(SAMPLE, 30);
        require(thirty.unstableFrames == 0,
                "35 ms deve ficar dentro da tolerância do alvo de 30 FPS");

        FrameStatsAnalyzer.Result empty = FrameStatsAnalyzer.parse("sem dados", 60);
        require(!empty.available, "saída sem framestats deve ficar indisponível");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
