# MiojoPlays Store — Site

Site estático mobile-first da loja digital MiojoPlays.

A loja é uma plataforma de múltiplos produtos. PulseBoost é apenas um item do catálogo e não define a identidade, a navegação ou a arquitetura do site.

## Arquitetura de páginas

Cada aba possui um assunto próprio. Conteúdo de uma área não deve ser despejado em outra página.

- `index.html` — **Início**: apresentação da marca, informações iniciais e orientação de navegação;
- `store.html` — **Loja**: catálogo comercial geral, busca, status, preço, versão e acesso aos produtos;
- `categorias.html` — **Categorias**: organização do catálogo e entrada para cada área;
- `apps.html` — **Apps**: somente aplicativos e informações relacionadas a apps;
- `ferramentas.html` — **Ferramentas**: somente utilitários, diagnóstico e ferramentas técnicas;
- `packs.html` — **Packs / Game Kits**: somente packs, presets, perfis e conteúdo específico;
- `suporte.html` — **Suporte**: bugs, download, compatibilidade, atualizações e políticas;
- `pulseboost.html` — página individual de produto do PulseBoost;
- futuras páginas `<produto>.html` — páginas independentes para cada novo produto;
- `privacy.html` e `terms.html` — páginas legais.

## Princípio de navegação

A interface é compartilhada, mas o conteúdo é separado por contexto:

1. **Início** apresenta a loja;
2. **Loja** concentra a área comercial;
3. **Categorias** organiza as famílias de produto;
4. **Apps / Ferramentas / Packs** mostram apenas itens e informações daquela família;
5. **Produto** detalha um item específico;
6. **Suporte** concentra pós-download, bugs e ajuda.

## Objetivos

- funcionar bem em celular;
- carregar sem framework pesado;
- suportar vários apps, ferramentas, packs e utilitários;
- manter página própria para cada produto;
- mostrar status, compatibilidade, versão, preço e entrega com clareza;
- não depender de banco, analytics ou checkout para existir;
- permitir evolução futura sem transformar a home em uma página única gigante.

## Publicação

A pasta `site/` é o diretório de assets estáticos servido pelo Cloudflare Worker atual. O endereço público da loja é configurado na camada de hospedagem e não deve ser acoplado a um produto específico.

## Próximas etapas

1. validar as abas separadas em deploy real e em tela mobile;
2. evoluir a página individual de produto para uma ficha comercial completa;
3. adicionar novos produtos nas respectivas categorias sem misturar conteúdo;
4. conectar checkout somente depois de definir produto, preço, licença, entrega e política de reembolso;
5. adicionar métricas apenas com política de privacidade atualizada.
