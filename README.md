# localhost:facom — Sistema de Vendas Transparentes

Aplicação desenvolvida por estudantes para gerenciar a venda de alimentos em uma sala de estudos cedida pelo departamento, com o objetivo de arrecadar fundos para melhorias no espaço. **Sem fins lucrativos.**

O cliente escaneia um QR Code fixo na sala, monta o pedido e paga via PIX; o pagamento é confirmado automaticamente. Todas as transações e o caixa atual ficam publicamente visíveis em um dashboard, garantindo total transparência sobre o uso do dinheiro arrecadado.

## Como funciona

1. Cliente escaneia o QR Code → abre o catálogo de produtos.
2. Monta o pedido (sem necessidade de cadastro — pedido anônimo).
3. Sistema gera uma cobrança PIX com QR Code e código copia-e-cola.
4. Pagamento é confirmado automaticamente via webhook.
5. Transação aparece no dashboard público em tempo real.

## Stack

- **Frontend**: React + Vite
- **Backend**: Spring Boot
- **Banco de dados**: PostgreSQL
- **Pagamentos**: Mercado Pago (PIX)

## Funcionalidades

- 🛒 Catálogo de produtos e carrinho (cliente)
- 💳 Geração de cobrança PIX e confirmação automática de pagamento
- 📊 Dashboard público com KPIs, transações e progresso da meta de arrecadação
- 🎯 Meta de arrecadação com link para vaquinha externa
- 🔐 Painel administrativo (produtos, pedidos, despesas, gestão de admins)

## Rodando o projeto localmente

### Pré-requisitos

- Java 25+
- Node.js 24+
- Podman ou Docker (Postgres e MinIO sobem via compose)

### Tudo de uma vez

```bash
./run.sh                  # sobe infra, API e frontend; Ctrl+C derruba os três
STOP_INFRA=1 ./run.sh     # também derruba os containers ao sair
```

Os passos abaixo são o mesmo, feito à mão.

### Infraestrutura

```bash
podman compose up -d
```

### Backend

```bash
cd api
APP_BOOTSTRAP_ADMIN_EMAIL=admin@localhost.facom \
APP_BOOTSTRAP_ADMIN_PASSWORD=troque-esta-senha \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

O perfil `dev` usa o provedor de pagamento `fake`, que confirma qualquer cobrança
após 10 segundos — dá para rodar o fluxo inteiro sem credenciais do Mercado Pago.

### Frontend

```bash
cd ui
npm install
npm run dev
```

O Vite faz proxy de `/api` para `localhost:8080`, então não é preciso configurar
`VITE_API_URL` em desenvolvimento.

### Rotas

| Rota | Tela |
|---|---|
| `/` | Portal de transparência |
| `/cardapio` | Cardápio e carrinho |
| `/pagamento/:orderId` | Pagamento PIX |
| `/confirmacao/:orderId` | Confirmação do pedido |
| `/admin/login` | Login do painel |
| `/admin/pedidos` | Pedidos: filtro por status, confirmação manual, sincronização e cancelamento |
| `/admin/produtos` | Produtos: cadastro, edição, imagem e remoção |
| `/admin/despesas` | Despesas e meta de arrecadação |
| `/admin/usuarios` | Gestão de admins |

> O QR Code fixo da sala precisa apontar para `/cardapio`, e não para a raiz —
> a raiz é o portal de transparência.

## Deploy da API

O `api/Dockerfile` já ativa o perfil `prod` e usa a porta injetada pela plataforma
(`PORT`). O contexto de build é a pasta `api/`, então configure o diretório raiz do
serviço como `api` no Render ou no Railway.

O `DATABASE_URL` pode vir tanto como URL JDBC quanto no formato que essas plataformas
realmente entregam (`postgres://usuario:senha@host:porta/banco`) — a aplicação converte.
As demais variáveis precisam ser definidas manualmente, e sem elas a API não sobe:

| Variável | Para quê |
|---|---|
| `APP_JWT_SECRET` | Assinatura dos tokens do painel; não tem padrão |
| `APP_PAYMENTS_PROVIDER` | Precisa ser `mercadopago` em produção |
| `APP_MERCADOPAGO_ACCESS_TOKEN` / `APP_MERCADOPAGO_WEBHOOK_SECRET` | Credenciais do PIX |
| `APP_STORAGE_*` | Bucket das imagens; o padrão aponta para o MinIO local |
| `APP_CORS_ORIGINS` | Origem do frontend publicado |

### Supabase + Render

A conexão direta do Supabase (`db.<ref>.supabase.co`) só tem endereço IPv6, e a
saída de rede do Render é só IPv4 — a conexão falha com
`java.net.SocketException: Network is unreachable`. Use a string de conexão do
pooler (Supavisor) em **session mode**, que tem IPv4:

```
postgresql://postgres.<ref>:<senha>@aws-0-<regiao>.pooler.supabase.com:5432/postgres
```

Repare que o usuário é `postgres.<ref>`, não `postgres`. Não use a porta `6543`
(transaction mode): ela não suporta prepared statements nem advisory locks de
sessão, e o Flyway usa os dois na migração de startup.

## Estrutura do repositório

```
.
├── api/       # API Spring Boot
├── ui/        # Aplicação React
├── run.sh     # Sobe o stack inteiro localmente
└── README.md
```

## Licença

Este projeto está sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para mais detalhes.
