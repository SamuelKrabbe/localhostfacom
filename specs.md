# Sala de Estudos — Sistema de Vendas Transparentes

## Contexto
Grupo de estudantes universitários com uma sala cedida pelo departamento. O grupo vende comida na sala para arrecadar fundos para melhorias no espaço. Sem fins lucrativos — transparência é requisito central, não opcional.

## Objetivo
Aplicação web que permite: (1) clientes comprarem produtos via QR Code e pagarem por PIX, (2) qualquer pessoa visualizar publicamente as transações e o caixa atual, (3) admins gerenciarem produtos e operações.

## Stack
- **Frontend**: React + Vite.
- **Backend**: Spring Boot.
- **Banco**: PostgreSQL (via Spring Data JPA).
- **PIX**: Mercado Pago (ver justificativa abaixo).
- **Deploy**: front na Vercel/Netlify; backend Spring Boot em host com runtime Java (Railway, Render, ou VPS); Postgres pode ser gerenciado (Railway, Neon, Supabase-só-DB) ou no mesmo host do backend.

## Fluxos

### 1. Cliente (compra)
1. Escaneia QR Code fixo na sala → abre o site (React, deployado).
2. Vê catálogo de produtos disponíveis (nome, preço, foto opcional).
3. Monta um "carrinho" e confirma o pedido — sem identificação (pedido anônimo).
4. Sistema gera um QR Code PIX (copia-e-cola + imagem) com o valor exato.
5. Sistema detecta o pagamento via webhook do Mercado Pago e marca o pedido como pago.
6. Cliente vê confirmação na tela.

**Ponto de atenção**: como o pedido é anônimo e não há controle de estoque no sistema, a entrega do produto depende de conferência manual na hora (ex: cliente mostra a tela de confirmação de pagamento pro admin que está vendendo).

### 2. Dashboard público (transparência)
- Lista de transações confirmadas (produto, valor, data/hora — sem dados pessoais, já que o pedido é anônimo).
- Caixa atual (total arrecadado − despesas registradas, se houver).
- KPIs: total vendido no dia/semana/mês, produto mais vendido, nº de pedidos, ticket médio.
- Barra de progresso da meta de arrecadação (ex: "R$ 800 / R$ 2000").
- Ao clicar na meta/barra → redireciona para a página da vaquinha (link externo) para quem quiser doar diretamente.

### 3. Admin (rota protegida)
- Login (email/senha + JWT via Spring Security).
- CRUD de produtos: nome, preço, foto, ativo/inativo (sem campo de estoque — controle é manual, fora do sistema).
- Visualizar/gerenciar pedidos (inclusive marcar como pago manualmente, se necessário — ex: falha de webhook).
- Registrar despesas (opcional, para o cálculo de caixa líquido).
- Definir/editar a meta de arrecadação e o link da vaquinha.
- Gerenciar outros admins (adicionar/remover por email) — necessário porque o cargo é rotativo.

## Decisões
- **Vaquinha**: ainda não existe — precisa ser criada (Vakinha ou similar) antes do lançamento; link fica configurável no admin.
- **Estoque**: não controlado pelo sistema — controle manual pelos admins. Pode ser adicionado depois sem grande refatoração (campo de estoque + validação no backend na hora do pedido).
- **Identidade do comprador**: pedido anônimo, sem nome/telefone.
- **PIX**: Mercado Pago — aceita conta pessoa física (sem precisar de CNPJ), taxa ~0,99%, SDK Java oficial, webhook de confirmação bem documentado. Alternativas (Asaas, Efí) têm free tier mas exigem CNPJ ou verificação mais burocrática para PF.
- **Admins**: cargo rotativo, hoje 2 pessoas. Modelar como tabela `admin` no Postgres (não hardcode) — assim trocar quem é admin é gerenciável pela própria aplicação, sem novo deploy. Login simples email/senha + JWT é suficiente para esse volume de usuários.

## Requisitos não-funcionais
- **Transparência**: dados de vendas públicos e verificáveis por qualquer visitante, sem exigir login.
- **Simplicidade**: você é o único a codar/manter — priorizar stack simples de operar sozinho, evitar infraestrutura que exija manutenção constante.
- **Baixo custo**: hospedagem gratuita/barata em todas as camadas.
- **Segurança**: rota admin protegida por autenticação; valor da cobrança PIX sempre calculado e validado no backend (nunca confiar em valor vindo do cliente); assinatura do webhook do Mercado Pago deve ser validada para evitar confirmação de pagamento falsa.

## Riscos / cuidados técnicos
- **Webhook pode falhar ou atrasar**: ter um fallback (endpoint/job que consulta o status do pagamento periodicamente, ou botão manual no admin) para não deixar pedido "pago" preso como pendente.
- **Concorrência**: como pedido é anônimo e sem estoque, não há risco de overselling — mas o cálculo de caixa/KPIs deve ler direto do banco (transações confirmadas), nunca de cache que pode ficar desatualizado.
- **Webhook público**: o endpoint que recebe callback do Mercado Pago precisa validar a assinatura da requisição — sem isso, qualquer um poderia forjar uma confirmação de pagamento.
