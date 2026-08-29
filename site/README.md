# MiojoPlays Store — Site

Site estático mobile-first da loja digital MiojoPlays.

A loja é uma plataforma de múltiplos produtos. PulseBoost é apenas um item do catálogo e não define a identidade, a navegação ou a arquitetura do site.

## Arquitetura visual

A experiência é organizada em três camadas claras:

1. **Interface** — topbar, navegação lateral no desktop e dock inferior no mobile.
2. **Miolo / Store Hub** — descoberta, categorias, status do ecossistema, atalhos e destaques.
3. **Loja / Catálogo** — busca, filtros, cards de produto e acesso às páginas comerciais.

Isso evita que a home seja apenas uma sequência de textos e cards. O usuário entra numa interface de loja, entende o ecossistema e só então navega para a camada comercial.

## Objetivos

- funcionar bem em celular;
- carregar sem framework pesado;
- suportar vários apps, ferramentas, packs e utilitários;
- manter página própria para cada produto;
- mostrar status, compatibilidade, versão, preço e entrega com clareza;
- não depender de banco, analytics ou checkout para existir;
- permitir migração futura para domínio próprio sem reescrever o frontend.

## Estrutura

- `index.html` — Store Hub / visão geral;
- `store.html` — camada comercial com catálogo, busca e filtros;
- `pulseboost.html` — página do PulseBoost;
- futuras páginas `<produto>.html` — páginas independentes dos próximos itens;
- `privacy.html` — política inicial de privacidade;
- `terms.html` — termos iniciais;
- `styles.css`, `v2.css`, `shop-v4.css`, `shop-v5.css` — base visual, catálogo e shell estrutural;
- `app.js` — menu mobile, filtros com deep links, ano e animações leves;
- `favicon.svg` — ícone local.

## Publicação

A pasta `site/` é o diretório de publicação estática. O projeto Cloudflare Worker que serve a loja deve usar nome e subdomínio neutros, sem referência a um produto específico.

## Próximas etapas

1. manter a identidade visual e navegação consistentes nas páginas de produto;
2. adicionar novos produtos como páginas independentes;
3. separar a loja em repositório próprio quando o fluxo permitir;
4. conectar checkout somente depois de definir produto, preço, licença, entrega e política de reembolso;
5. adicionar métricas apenas com política de privacidade atualizada.
