package io.github.astromg01.pulseboost;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts the optimizer's raw decisions into an explanation a normal user can audit.
 *
 * <p>This class does not execute commands and does not change device state. It exists so every
 * recommended profile can answer four questions before/after a session: why this profile was
 * selected, what will be attempted, what benefit is realistically expected and how the changes
 * are reverted.</p>
 */
final class OptimizationAdvisor {
    static final class Explanation {
        final String uiDetail;
        final String reportDetail;

        Explanation(String uiDetail, String reportDetail) {
            this.uiDetail = uiDetail;
            this.reportDetail = reportDetail;
        }
    }

    private OptimizationAdvisor() {
    }

    static Explanation explain(
            DeviceStats stats,
            Optimizer.Options options,
            boolean adaptive,
            boolean trimCache,
            boolean disableSaver,
            String gameMode) {
        double memoryRatio = stats.totalMemory > 0
                ? (double) stats.availableMemory / stats.totalMemory
                : 0.0;
        boolean memoryPressure = stats.lowMemory
                || memoryRatio < 0.34
                || stats.availableMemory < 1150L * 1024L * 1024L;
        boolean hot = stats.batteryTemperature >= 41.5f;
        boolean warm = stats.batteryTemperature >= 38.5f
                || (stats.charging && stats.batteryTemperature >= 37.5f);
        boolean lowBattery = stats.batteryLevel >= 0
                && stats.batteryLevel < 20
                && !stats.charging;

        String reason;
        String expectedImpact;
        if (!adaptive) {
            reason = "Perfil manual: respeitando exatamente as opções escolhidas";
            expectedImpact = "depende dos recursos ativados e do gargalo real do jogo";
        } else if (hot) {
            reason = String.format(Locale.getDefault(),
                    "%.1f °C: calor já alto, então estabilidade vem antes de potência",
                    stats.batteryTemperature);
            expectedImpact = "evitar agravar thermal throttling; não há promessa de ganho de FPS";
        } else if (warm) {
            reason = String.format(Locale.getDefault(),
                    "%.1f °C: temperatura em elevação, evitando um perfil agressivo",
                    stats.batteryTemperature);
            expectedImpact = "manter frame pacing mais sustentável durante uma sessão longa";
        } else if (lowBattery) {
            reason = stats.batteryLevel
                    + "% de bateria: equilibrando desempenho e consumo";
            expectedImpact = "reduzir interferências sem aumentar desnecessariamente o gasto de energia";
        } else if (memoryPressure) {
            reason = "Pressão de RAM detectada: priorizando memória livre antes do jogo";
            expectedImpact = "pode reduzir recargas e stutter ligados à falta de memória";
        } else {
            reason = "Temperatura, bateria e memória permitem o perfil de desempenho";
            expectedImpact = "reduzir interferências evitáveis; o limite do jogo/GPU continua igual";
        }

        List<String> actions = new ArrayList<>();
        if (gameMode != null) {
            actions.add("Game Mode " + ("performance".equals(gameMode) ? "Performance" : "Standard")
                    + " quando suportado pelo Android");
        }
        if (trimCache) {
            actions.add("liberar somente processos que o Android já mantém em cache");
        }
        if (disableSaver) {
            actions.add("desativar temporariamente a economia de bateria");
        }
        if (options.doNotDisturb) {
            actions.add("silenciar notificações comuns se o acesso ao Não Perturbe estiver concedido");
        }
        if (options.animations) {
            actions.add("usar animações do sistema em 0,5× durante a sessão");
        }
        if (options.frameSense) {
            actions.add("usar FrameSense como orientação de FPS, sem interpolar quadros");
        }
        if (actions.isEmpty()) {
            actions.add("monitorar a sessão sem forçar um ajuste desnecessário");
        }

        String primaryAction = actions.get(0);
        String uiDetail = reason + " • " + primaryAction;
        String reportDetail = reason
                + ". Ações previstas: " + join(actions)
                + ". Impacto esperado: " + expectedImpact
                + ". Risco: baixo; sem root, sem alterar limites térmicos, governor, driver ou arquivos do jogo"
                + ". Reversão: configurações temporárias são registradas e restauradas ao encerrar a sessão";
        return new Explanation(uiDetail, reportDetail);
    }

    private static String join(List<String> actions) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < actions.size(); index++) {
            if (index > 0) {
                result.append(index == actions.size() - 1 ? " e " : ", ");
            }
            result.append(actions.get(index));
        }
        return result.toString();
    }
}
