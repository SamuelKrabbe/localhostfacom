interface CartBarProps {
  totalItems: number;
  totalPrice: number;
  onCheckout: () => void;
  isLoading: boolean;
}

export function CartBar({ totalItems, totalPrice, onCheckout, isLoading }: CartBarProps) {
  if (totalItems === 0) return null;

  return (
    <div className="fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-gray-200 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)] pb-safe">
      <div className="max-w-md mx-auto flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-600">{totalItems} {totalItems === 1 ? 'item' : 'itens'}</p>
          <p className="text-lg font-semibold text-gray-900">
            {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(totalPrice)}
          </p>
        </div>
        <button
          onClick={onCheckout}
          disabled={isLoading}
          className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-lg font-medium transition-colors disabled:opacity-70 flex items-center"
        >
          {isLoading ? 'Processando...' : 'Finalizar pedido'}
        </button>
      </div>
    </div>
  );
}
