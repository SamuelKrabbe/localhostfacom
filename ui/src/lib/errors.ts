import { ApiError } from '../api/client';

const MESSAGES: Record<string, string> = {
  'empty-cart': 'Seu carrinho está vazio.',
  'product-not-found': 'Um dos produtos não está mais disponível.',
  'product-inactive': 'Um dos produtos saiu do cardápio. Revise seu pedido.',
  'order-not-found': 'Pedido não encontrado.',
  'order-not-pending': 'Este pedido já foi finalizado.',
  'order-has-no-charge': 'A cobrança deste pedido ainda não foi gerada.',
  'payment-provider-error': 'O sistema de pagamento está indisponível. Tente de novo em instantes.',
  'rate-limited': 'Muitas tentativas seguidas. Aguarde alguns segundos.',
  'validation-failed': 'Confira os dados do pedido e tente de novo.',
  'internal-error': 'Algo deu errado do nosso lado. Tente de novo.',
};

const FALLBACK = 'Não foi possível completar a operação. Tente de novo.';
const OFFLINE = 'Sem conexão com o servidor. Verifique sua internet.';

export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return MESSAGES[error.slug] ?? FALLBACK;
  }
  // fetch rejects with a TypeError when the network itself is unreachable.
  if (error instanceof TypeError) {
    return OFFLINE;
  }
  return FALLBACK;
}
