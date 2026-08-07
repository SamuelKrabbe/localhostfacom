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
  'validation-failed': 'Confira os dados e tente de novo.',
  'internal-error': 'Algo deu errado do nosso lado. Tente de novo.',

  // Admin area
  'invalid-credentials': 'E-mail ou senha incorretos.',
  'not-authenticated': 'Sua sessão expirou. Entre de novo.',
  'admin-exists': 'Já existe um admin com esse e-mail.',
  'admin-not-found': 'Admin não encontrado.',
  'cannot-remove-self': 'Você não pode remover a si mesmo.',
  'cannot-remove-last-admin': 'É preciso manter pelo menos um admin ativo.',
  'expense-not-found': 'Despesa não encontrada.',
  'invalid-goal': 'A meta precisa ser maior que zero.',
  'empty-file': 'Nenhum arquivo foi enviado.',
  'unreadable-upload': 'Não foi possível ler o arquivo enviado.',
  'unsupported-image': 'Formato de imagem não suportado. Use JPEG, PNG ou WebP.',
  'image-too-large': 'A imagem é grande demais. O limite é 8192 pixels por lado.',
  'image-not-found': 'Imagem não encontrada.',
  'image-in-use': 'Esta imagem está sendo usada por um produto.',
  'image-resize-failed': 'Não foi possível redimensionar a imagem.',
  'image-encode-failed': 'Não foi possível processar a imagem.',
  'image-save-failed': 'Não foi possível salvar a imagem.',
  'storage-upload-failed': 'Falha ao enviar a imagem para o armazenamento.',
  'storage-delete-failed': 'Falha ao remover a imagem do armazenamento.',
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
