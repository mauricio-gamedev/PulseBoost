# PulseBoost como produto digital

O PulseBoost é o primeiro utilitário gamer da linha de produtos digitais mantida por @astromg01. A prioridade é resolver problemas reais de desempenho e estabilidade no Android sem vender promessas impossíveis.

## Regra principal

Se uma função não economiza tempo, não reduz um problema mensurável ou não melhora a compreensão do usuário sobre o aparelho, ela não entra como recurso pago nem como promessa de marketing.

## Problemas que o produto tenta resolver

- dificuldade para entender se o gargalo atual é calor, RAM, energia ou armazenamento;
- configurações gamer espalhadas e pouco explicadas;
- boosters que aplicam comandos sem informar o efeito ou como desfazer;
- stutter causado por pressão de memória, economia de bateria, aquecimento ou compilação durante o uso;
- falta de um relatório simples que diferencie ajuste aplicado, ignorado e não suportado.

## Compromissos técnicos

1. Toda alteração temporária precisa ter caminho de restauração.
2. Nenhuma função pode remover proteção térmica, alterar governor/voltagem, trocar driver ou modificar arquivos do jogo.
3. O app não promete FPS que o hardware ou o jogo não conseguem entregar.
4. Recursos dependentes de Shizuku devem falhar de forma segura quando não forem suportados.
5. Diagnóstico e histórico permanecem locais por padrão.
6. Cada otimização deve explicar motivo, impacto esperado, risco e reversão.
7. Uma melhoria só vira argumento de venda depois de validada em aparelho real.

## Primeira expansão de produto

### Plano de Otimização Explicável

O motor adaptativo passa a produzir uma explicação auditável junto do perfil escolhido:

- **Por quê:** condição que levou à decisão, como calor, RAM, bateria ou estado saudável;
- **O que será tentado:** Game Mode, limpeza seletiva de cache, economia de bateria, Não Perturbe, animações e FrameSense conforme as opções;
- **Impacto esperado:** benefício realista relacionado ao gargalo detectado;
- **Risco:** declaração explícita das limitações e do que o PulseBoost não altera;
- **Reversão:** confirmação de que configurações temporárias são registradas para restauração.

Essa camada não executa nenhum comando novo. Ela torna o comportamento já existente compreensível antes de ampliarmos o alcance do produto.

## Próximas etapas planejadas

- matriz de compatibilidade por fabricante/versão Android;
- perfis por categoria de jogo em vez de presets cegos por marca;
- recomendação gráfica orientada por capacidade do aparelho;
- exportação de relatório para suporte, mantendo dados sensíveis fora do arquivo;
- catálogo de ajustes com status `suportado`, `ignorado` ou `indisponível`;
- validação em aparelhos além do Galaxy A06 antes de anunciar suporte amplo.

## Critério para começar a vender

A primeira versão comercial não deve ser publicada até cumprir, no mínimo:

- instalação e restauração verificadas em aparelhos físicos compatíveis;
- nenhuma configuração temporária deixada presa após reinício/fechamento inesperado nos testes principais;
- comportamento térmico validado sem promessas de ganho quando o aparelho já está em throttling;
- relatório legível por uma pessoa que não conhece comandos Android;
- política de licença, atualização, suporte e reembolso definida antes do checkout;
- página de venda descrevendo limites com a mesma clareza usada para benefícios.

O objetivo é vender utilidade e confiança, não números de FPS inventados.
