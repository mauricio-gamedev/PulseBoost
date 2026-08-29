# MiojoPlays Store — Site

Site estático mobile-first da loja digital gamer MiojoPlays.

A loja é uma plataforma de múltiplos produtos. PulseBoost continua como produto próprio, mas não define a identidade nem o catálogo principal da loja.

## Arquitetura de páginas

Cada área possui assunto próprio:

- `index.html` — **Início**: apresentação, destaques e entrada para o catálogo;
- `store.html` — **Loja**: catálogo geral e busca;
- `categorias.html` — **Categorias**: mapa das famílias de produto;
- `recargas.html` — **Recargas**: créditos e recargas gamer;
- `giftcards.html` — **Gift Cards**: cartões-presente digitais;
- `assinaturas.html` — **Assinaturas**: planos e acessos digitais autorizados;
- `jogos.html` — **Jogos & Keys**: jogos, DLCs, expansões e passes com origem autorizada;
- `miojoplays.html` — **Produtos MiojoPlays**: apps, ferramentas e conteúdo desenvolvido pela própria MiojoPlays;
- `apps.html`, `ferramentas.html` e `packs.html` — subáreas dos produtos MiojoPlays;
- `pulseboost.html` — página individual do PulseBoost;
- `suporte.html` — suporte técnico, downloads, compatibilidade e futuros pedidos digitais;
- `privacy.html` e `terms.html` — páginas legais.

## Catálogo de revenda

As categorias externas são preparadas no frontend, mas não publicam marcas, valores, estoque ou promessas de entrega sem fornecedor autorizado e disponibilidade confirmada.

Estado atual:

1. fornecedor de produtos digitais em processo de análise/aprovação;
2. catálogo externo ainda não integrado;
3. checkout e automação de entrega ainda não ativados;
4. produtos MiojoPlays continuam independentes e podem permanecer disponíveis normalmente.

## Modelo futuro de integração

O desenho previsto separa as responsabilidades:

1. frontend exibe catálogo e detalhes;
2. backend privado consulta fornecedor e cria pedidos;
3. gateway confirma pagamento por webhook;
4. backend processa o produto no fornecedor;
5. pedido recebe status de processamento e entrega;
6. segredos de API permanecem apenas no backend.

## Objetivos

- funcionar bem em celular;
- carregar sem framework pesado;
- suportar recargas, gift cards, assinaturas, jogos digitais e produtos próprios;
- manter página própria para cada categoria e produto;
- mostrar região, compatibilidade, disponibilidade, preço e entrega com clareza;
- não inventar produtos ou denominações antes da integração real;
- permitir evolução para checkout e entrega automática sem acoplar a interface ao fornecedor.

## Publicação

A pasta `site/` é o diretório de assets estáticos servido pelo Cloudflare Worker atual. O endereço público da loja é configurado na camada de hospedagem.

## Próximas etapas

1. validar o novo posicionamento em deploy real e telas mobile;
2. concluir aprovação de fornecedor autorizado;
3. integrar catálogo real em ambiente de teste;
4. definir gateway e estados do pedido;
5. só então ativar preço, pagamento e entrega automática para produtos de revenda;
6. atualizar termos e privacidade antes de processar compras reais.
