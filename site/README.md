# MiojoPlays Store — Site

Site estático mobile-first da **MiojoPlays Store**, agora focado exclusivamente em comércio gamer digital.

## Escopo público

A loja trabalha a experiência de compra em quatro famílias:

- `recargas.html` — recargas, moedas e créditos gamer;
- `giftcards.html` — cartões-presente e códigos digitais;
- `assinaturas.html` — planos e assinaturas gamer;
- `jogos.html` — jogos, keys, DLCs, expansões e passes.

Apps, APKs, ferramentas de otimização e packs próprios não fazem mais parte da vitrine da MiojoPlays Store.

## Arquitetura de páginas

- `index.html` — **Início**: posicionamento da loja, categorias e padrão de compra;
- `store.html` — **Loja**: catálogo geral, busca e filtros por categoria;
- `categorias.html` — **Categorias**: comparação e entrada para cada família;
- `recargas.html` — **Recargas**: requisitos, dados e fluxo de top-up;
- `giftcards.html` — **Gift Cards**: valor, moeda, região e resgate;
- `assinaturas.html` — **Assinaturas**: duração, elegibilidade e ativação;
- `jogos.html` — **Jogos & Keys**: plataforma, edição, região e conteúdo;
- `suporte.html` — **Suporte**: orientações para produtos e futuros pedidos;
- `privacy.html` — política de privacidade;
- `terms.html` — termos de uso.

## Estado comercial

A interface está pronta para receber catálogo real, mas o checkout ainda não está ativo. Enquanto isso:

1. categorias podem ser exibidas;
2. produtos, marcas, preços e estoque externos não devem ser inventados;
3. itens só entram na vitrine quando houver disponibilidade comercial confirmada;
4. regras de região, moeda, plataforma, duração e entrega devem vir junto ao produto;
5. pagamentos, pedidos e fulfillment serão adicionados somente quando a integração comercial estiver definida.

## Princípios de UX

- mobile-first;
- navegação curta e consistente;
- uma finalidade clara por página;
- informação crítica antes do CTA;
- status de disponibilidade explícito;
- nenhuma solicitação de senha ou 2FA para recargas/suporte;
- sem prometer entrega, estoque ou tempo de processamento que o backend ainda não confirme.

## Estrutura futura de integração

Quando o catálogo comercial for liberado, a camada pública deve consumir dados de um backend próprio. Credenciais de fornecedor e pagamento nunca devem ficar no JavaScript do navegador.

Fluxo esperado de pedido:

`CRIADO → AGUARDANDO_PAGAMENTO → PAGO → PROCESSANDO → ENTREGUE`

Estados de falha/reembolso devem ser tratados separadamente antes de qualquer venda real.

## Publicação

A pasta `site/` continua sendo o diretório de assets estáticos servido pela hospedagem atual. A loja deve permanecer desacoplada do produto Android existente no restante do repositório, mesmo enquanto os dois projetos ainda compartilham este repositório.
