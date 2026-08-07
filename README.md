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

- Java 25+ e Maven
- Node.js 24+
- PostgreSQL

### Backend

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# preencher credenciais do banco e do Mercado Pago
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
cp .env.example .env
# preencher URL da API
npm install
npm run dev
```

## Estrutura do repositório

```
.
├── backend/     # API Spring Boot
├── frontend/    # Aplicação React
└── README.md
```

## Licença

Este projeto está sob a licença MIT — veja o arquivo [LICENSE](LICENSE) para mais detalhes.
