# PulseBoost

### Game Mode inteligente e reversível para Galaxy A06

Aplicativo Android para preparar o Galaxy A06 antes de abrir um jogo, reduzir interferências evitáveis e restaurar as configurações ao terminar. A versão atual é **0.3.1 beta** e usa o identificador definitivo `io.github.astromg01.pulseboost`.

**Projeto criado, dirigido e mantido por [@astromg01](https://github.com/astromg01).**

> Código publicamente auditável com todos os direitos reservados. A visibilidade deste repositório não autoriza cópia, modificação, redistribuição ou comercialização. Consulte [LICENSE](LICENSE) e [AUTHORS.md](AUTHORS.md).

## O que esta beta faz

- mostra RAM livre, temperatura da bateria, armazenamento, energia e um diagnóstico de prontidão;
- escolhe automaticamente entre desempenho, recuperação de memória, equilíbrio energético e estabilidade térmica;
- usa o **FrameSense** para recomendar um alvo sustentável e divisível pela taxa da tela, sem forçar o FPS do jogo;
- atualiza essa recomendação durante a partida conforme calor, bateria e memória mudam;
- analisa localmente os tempos de quadros recentes ao encerrar, quando o Android disponibiliza os dados;
- mostra estabilidade, p95 e quantidade de quadros no histórico da última partida;
- após duas leituras térmicas altas, pode voltar o Game Mode para Standard somente se o próprio PulseBoost tiver ativado Performance;
- lista os aplicativos instalados e abre o jogo selecionado;
- usa o Shizuku para executar apenas ajustes de identidade `shell` autorizados pelo usuário;
- desliga temporariamente a economia de bateria;
- libera processos que o próprio Android mantém em cache, sem escolher pacotes para forçar parada;
- ativa o modo de jogo `performance` somente quando o Android informa que ele é suportado;
- silencia notificações comuns durante a sessão, se o usuário conceder acesso ao Não Perturbe;
- oferece transições do sistema a 0,5× como opção separada e desativada por padrão;
- pode solicitar uma pré-compilação ART do jogo com o modo `speed`;
- mantém uma sessão visível em primeiro plano enquanto o jogo está aberto;
- mostra temperatura, RAM e duração na notificação, com ação para restaurar imediatamente;
- detecta localmente quando o jogo foi fechado e restaura os ajustes após uma margem de segurança;
- guarda duração, pico térmico, menor RAM livre e perfil da última partida;
- guarda os valores anteriores e tenta restaurá-los automaticamente mesmo sem a tela principal aberta.

## FrameSense: o que ele é

O FrameSense é um orientador de **frame pacing**, não um gerador de quadros. Ele combina taxa atual da tela, temperatura da bateria, carga, nível de bateria e pressão de RAM para sugerir um alvo coerente. Em uma tela de 60 Hz, por exemplo, os alvos naturais são 60 ou 30 FPS; em 90 Hz, 90, 45 ou 30 FPS.

Durante a sessão, o alvo pode cair de forma recomendada se o aparelho esquentar. Ao terminar, o PulseBoost consulta `dumpsys gfxinfo <pacote> framestats` por Shizuku e resume apenas os dados recentes que o Android expuser. O aplicativo não captura a tela, não desenha overlay, não injeta bibliotecas, não altera `device_config` e não modifica arquivos do jogo.

O alvo exibido é uma recomendação para você usar nas configurações do jogo quando existir opção de FPS. O PulseBoost não promete que o jogo ou a GPU conseguirão sustentá-lo.

## Como funciona o motor inteligente

- **Desempenho inteligente:** tenta o Game Mode Performance quando temperatura, carga e memória permitem.
- **Recuperação de memória:** libera processos em cache somente quando existe pressão real de RAM.
- **Estabilidade térmica:** usa o modo Standard se a bateria já estiver aquecida ou o aparelho estiver esquentando enquanto carrega.
- **Equilíbrio energético:** evita o perfil mais agressivo quando a bateria está abaixo de 20%.
- **Proteção anti-throttle:** não força Performance nem desliga a economia de bateria quando a leitura térmica já está alta.

As decisões usam a temperatura da bateria como aproximação do estado térmico. Não existe acesso direto à temperatura interna da CPU/GPU por APIs comuns do Android.

## Instalação sem computador

> **Migração da versão anterior:** a v0.3.1 adotou um novo identificador Android. Desinstale o PulseBoost antigo de pacote `com.anne.pulseboost` antes de instalar esta versão para evitar dois ícones e duas configurações separadas.

1. Baixe o [APK oficial da versão 0.3.1 beta](downloads/PulseBoost-v0.3.1-beta.apk). Se necessário, autorize o navegador ou o GitHub a instalar aplicativos desconhecidos.
2. Instale o [Shizuku pelo site oficial](https://shizuku.rikka.app/download/).
3. No Galaxy A06, ative **Opções do desenvolvedor** e **Depuração sem fio**.
4. No Shizuku, use o pareamento por depuração sem fio e inicie o serviço.
5. Em **Execução inteligente**, ative **Acesso ao uso**. Ele serve somente para reconhecer quando o jogo sai da tela.
6. Abra as configurações de bateria do PulseBoost e selecione **Sem restrições**. Na Samsung, mantenha o app fora da suspensão profunda.
7. Abra o PulseBoost, toque em **Autorizar**, escolha o jogo e use **Otimizar e jogar**.

O Shizuku sem root precisa ser iniciado novamente depois que o aparelho reinicia. O Android 11 ou superior permite fazer isso no próprio telefone pela depuração sem fio.

## Limites importantes

O PulseBoost não faz interpolação nem geração real de quadros. Esse tipo de recurso exigiria integração do próprio jogo, do driver ou captura/injeção no pipeline gráfico, o que acrescentaria risco de latência, artefatos e incompatibilidade. O app também não troca driver de GPU, não aumenta a frequência do processador, não remove proteção térmica e não cria RAM.

Ele não consegue corrigir stutter causado pelo próprio jogo, compilação de shaders, servidor, internet ou limite do Helio G85. Temperatura baixa, espaço livre e configuração gráfica adequada continuam sendo os fatores mais importantes no A06. A análise pós-partida pode ficar indisponível em jogos que não exponham `framestats` compatíveis.

A temperatura exibida é a da bateria, usada apenas como indicador aproximado do calor do aparelho — não é uma leitura direta da CPU ou GPU.

## Privacidade e segurança

- sem anúncios, analytics ou permissão de internet;
- nenhum dado é enviado para fora do aparelho;
- nenhuma imagem da tela é capturada e nenhum overlay é usado;
- o histórico e a telemetria da sessão ficam apenas em preferências locais;
- o Acesso ao uso é consultado somente durante uma sessão ativa para identificar o aplicativo em primeiro plano;
- comandos recebem apenas o pacote selecionado, validado antes de ser usado;
- mudanças de Game Mode feitas fora do PulseBoost durante a partida são detectadas e preservadas;
- não há alteração de limites térmicos, governor, voltagem, partições ou arquivos de outros aplicativos;
- o usuário pode abrir o relatório para conferir quais ajustes funcionaram ou foram ignorados.

## Compilar o projeto

O script `build.sh` usa Android SDK Platform 36, Build Tools 36.1.0 e Java 17. As bibliotecas Shizuku 13.1.5 e AndroidX Annotation 1.3.0 estão preservadas em `vendor/` com as respectivas licenças.

```bash
export ANDROID_SDK_ROOT=/caminho/do/android-sdk
./build.sh
```

Sem credenciais, o script produz `dist/PulseBoost-v0.3.1-beta-unsigned.apk`. Para uma compilação assinada, informe localmente `PULSEBOOST_KEYSTORE`, `PULSEBOOST_KEY_ALIAS`, `PULSEBOOST_STORE_PASSWORD` e `PULSEBOOST_KEY_PASSWORD`. Nenhuma chave ou senha oficial é armazenada no repositório.

Somente APKs publicados por @astromg01 com o certificado documentado em [SECURITY.md](SECURITY.md) são versões oficiais. Para uma futura Play Store, a chave de upload será mantida separada e o Play App Signing protegerá a chave de distribuição.

## Autoria, distribuição e apoio

- titular e mantenedor: **[@astromg01](https://github.com/astromg01)**;
- pacote Android definitivo: `io.github.astromg01.pulseboost`;
- repositório oficial: [github.com/astromg01/PulseBoost](https://github.com/astromg01/PulseBoost);
- licença do código original: **todos os direitos reservados**;
- páginas futuras de Play Store, Patreon ou apoio só serão oficiais quando vinculadas neste repositório pelo titular.

## Referências técnicas

- [Shizuku API e guia oficial](https://github.com/RikkaApps/Shizuku-API)
- [Visibilidade de pacotes no Android](https://developer.android.com/training/package-visibility/declaring)
- [Game Mode interventions](https://developer.android.com/games/optimize/adpf/gamemode/gamemode-interventions)
- [FPS throttling e divisores de taxa da tela](https://developer.android.com/games/optimize/adpf/gamemode/fps-throttling)
- [Diagnóstico de frames com dumpsys](https://developer.android.com/tools/dumpsys)
- [Medição e pré-compilação de desempenho](https://developer.android.com/topic/performance/measuring-performance)
- [BatteryManager](https://developer.android.com/reference/android/os/BatteryManager)
- [Tipos de serviço em primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)

## Estado de validação

O código é compilado em modo release, alinhado, assinado e verificado com os esquemas APK v2 e v3. O analisador de `framestats` possui teste local para alvos de 60 e 30 FPS. O pacote público é auditado para impedir inclusão de keystore, senha, artefatos privados, overlay, captura de tela ou comandos persistentes perigosos. A instalação, o serviço persistente, a disponibilidade de `framestats` e os comandos Samsung ainda precisam ser confirmados em um Galaxy A06 físico; funções não suportadas são registradas como ignoradas em vez de serem forçadas.
