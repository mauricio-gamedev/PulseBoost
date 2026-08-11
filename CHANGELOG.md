# Histórico de versões

## 0.3.1 beta — 2026-08-11

- identidade pública migrada para `io.github.astromg01.pulseboost` antes de uma futura Play Store;
- autoria e titularidade registradas exclusivamente como @astromg01;
- licença MIT removida e código original marcado como todos os direitos reservados;
- `CODEOWNERS`, `AUTHORS.md`, `CITATION.cff` e política de autenticidade adicionados;
- chave privada, senhas, keystores e artefatos locais bloqueados no repositório público;
- script público gera APK não assinado por padrão e aceita assinatura somente por variáveis locais;
- certificado oficial documentado para conferência de APKs;
- interface identifica @astromg01 como criador e mantenedor.

## 0.3.0 beta — 2026-08-11

- FrameSense com alvo de FPS sustentável baseado na taxa da tela, calor, bateria e RAM;
- recomendação dinâmica durante a partida, sem forçar FPS nem alterar o jogo;
- análise local de `framestats` no encerramento, com estabilidade, mediana e p95;
- histórico expandido com alvo recomendado, taxa da tela e resultado dos quadros;
- proteção térmica após duas amostras altas, limitada ao Game Mode Performance aplicado pelo próprio PulseBoost;
- alterações manuais ou do Game Booster durante a sessão são detectadas e não são sobrescritas;
- nenhum uso de interpolação, captura de tela, overlay, injeção ou `device_config` persistente;
- leitura de comandos grandes drenada com limite seguro para evitar travamento do serviço Shizuku;
- teste local do analisador para distinguir corretamente metas de 60 e 30 FPS.

## 0.2.0 beta — 2026-08-11

- motor adaptativo que escolhe potência, recuperação de memória ou estabilidade térmica;
- serviço em primeiro plano `specialUse` compatível com Android 16;
- notificação persistente com temperatura, RAM, tempo de sessão e ação Restaurar;
- detecção local do fim da partida com Acesso ao uso e intervalo contra falsos positivos;
- restauração automática mesmo quando a tela principal não está aberta;
- recuperação do monitor após o processo ser recriado pelo Android;
- histórico local da última partida com duração, pico térmico, RAM mínima e motivo do encerramento;
- atalhos guiados para Acesso ao uso e bateria Sem restrições na Samsung;
- pedido opcional de notificações no momento certo;
- perfil térmico que evita forçar Performance quando o A06 já está quente ou com pouca bateria.

## 0.1.0 beta — 2026-08-11

- primeira versão funcional para Galaxy A06;
- integração com Shizuku por UserService;
- diagnóstico de RAM, bateria, armazenamento e economia de energia;
- seleção e inicialização de jogos;
- perfil temporário com limpeza de cache, economia de bateria, Game Mode, foco e animações opcionais;
- restauração automática e recuperação de sessão pendente;
- pré-compilação ART opcional;
- relatório por recurso, sem anúncios ou acesso à internet.
