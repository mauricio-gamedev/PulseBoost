# MiojoPlays Labs — Site V1

Site estático mobile-first para apresentar produtos digitais gamer do ecossistema MiojoPlays.

## Objetivos da V1

- funcionar bem em celular;
- carregar sem framework pesado;
- não depender de banco, analytics ou checkout para existir;
- apresentar produtos com limites e utilidade claros;
- permitir migração futura para domínio próprio sem reescrever o frontend.

## Estrutura

- `index.html` — home, produtos, princípios e suporte;
- `pulseboost.html` — página do primeiro produto;
- `privacy.html` — política inicial de privacidade;
- `terms.html` — termos iniciais;
- `styles.css` — layout e responsividade;
- `app.js` — menu mobile, ano e animações leves;
- `favicon.svg` — ícone local.

## Publicação

A pasta `site/` pode ser usada como diretório de build em hospedagens estáticas. Nenhum endereço de produção deve ser tratado como definitivo até a publicação ser validada.

## Próximas etapas

1. validar o site em deploy real;
2. separar para repositório próprio quando a criação de repositório estiver disponível no fluxo;
3. adicionar um domínio próprio sem alterar as URLs internas do site;
4. conectar checkout somente depois de definir produto, preço, licença, entrega e política de reembolso;
5. adicionar métricas apenas com política de privacidade atualizada.
