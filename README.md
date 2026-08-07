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

> O QR Code fixo da sala precisa apontar para `/cardapio`, e não para a raiz —
> a raiz é o portal de transparência.

## Estrutura do repositório

```
.
├── api/     # API Spring Boot
├── ui/      # Aplicação React
├── docs/    # Especificações e planos de implementação
└── README.md
```

## Licença

Este projeto está sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para mais detalhes.
