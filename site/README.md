# MiojoPlays Store — Site

Site estático mobile-first da loja digital MiojoPlays.

A loja é uma plataforma de múltiplos produtos. PulseBoost é apenas um item do catálogo e não define a identidade, a navegação ou a arquitetura do site.

## Objetivos

- funcionar bem em celular;
- carregar sem framework pesado;
- suportar vários apps, ferramentas, packs e utilitários;
- manter página própria para cada produto;
- mostrar status, compatibilidade, versão, preço e entrega com clareza;
- não depender de banco, analytics ou checkout para existir;
- permitir migração futura para domínio próprio sem reescrever o frontend.

## Estrutura

- `index.html` — home institucional e entrada do catálogo;
- `store.html` — catálogo geral e filtros;
- `pulseboost.html` — página de produto do PulseBoost;
- futuras páginas `<produto>.html` — páginas independentes dos próximos itens;
- `privacy.html` — política inicial de privacidade;
- `terms.html` — termos iniciais;
- `styles.css`, `v2.css`, `shop-v4.css` — layout e responsividade;
- `app.js` — menu mobile, filtros, ano e animações leves;
- `favicon.svg` — ícone local.

## Publicação

A pasta `site/` é o diretório de publicação estática. O nome do projeto de hospedagem deve representar a loja, não um produto específico.

Se o projeto Cloudflare Pages atual usa um subdomínio `*.pages.dev` baseado em PulseBoost, o endereço deve ser substituído por um novo projeto Pages com nome curto e neutro de loja. O conteúdo continua vindo deste repositório enquanto a loja não for separada em repositório próprio.

## Próximas etapas

1. publicar a home e catálogo multi-produto;
2. trocar o endereço de produção por um nome curto e neutro;
3. separar a loja em repositório próprio quando o fluxo permitir;
4. adicionar novos produtos como páginas independentes;
5. conectar checkout somente depois de definir produto, preço, licença, entrega e política de reembolso;
6. adicionar métricas apenas com política de privacidade atualizada.
