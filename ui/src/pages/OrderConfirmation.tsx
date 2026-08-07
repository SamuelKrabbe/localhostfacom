import { CheckCircle2, ChevronRight } from 'lucide-react';
import type { CartItem } from '../types';

interface OrderConfirmationProps {
  items: CartItem[];
  totalValue: number;
  onNewOrder: () => void;
}

export function OrderConfirmation({ items, totalValue, onNewOrder }: OrderConfirmationProps) {
  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-md mx-auto">
        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 flex flex-col items-center text-center mb-4">
          <div className="bg-green-100 p-3 rounded-full mb-4">
            <CheckCircle2 size={40} className="text-green-600" />
          </div>
          <h2 className="text-2xl font-semibold text-gray-900 mb-2">Pagamento Confirmado!</h2>
          <p className="text-gray-600 text-sm mb-6">
            Obrigado! Todo o valor arrecadado é destinado exclusivamente para a manutenção e melhoria da nossa sala de estudos.
          </p>
          
          <div className="w-full border-t border-gray-100 pt-6">
            <h3 className="text-left font-medium text-gray-900 mb-4">Resumo do pedido:</h3>
            <ul className="space-y-3 mb-4">
              {items.map(item => (
                <li key={item.id} className="flex justify-between text-sm text-gray-600">
                  <span>{item.quantity}x {item.name}</span>
                  <span>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(item.price * item.quantity)}</span>
                </li>
              ))}
            </ul>
            <div className="flex justify-between items-center border-t border-gray-100 pt-4 font-semibold text-gray-900">
              <span>Total</span>
              <span>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(totalValue)}</span>
            </div>
          </div>
        </div>

        <button 
          onClick={onNewOrder}
          className="w-full flex items-center justify-center space-x-2 bg-blue-600 hover:bg-blue-700 text-white py-3 px-4 rounded-lg font-medium transition-colors"
        >
          <span>Fazer novo pedido</span>
          <ChevronRight size={18} />
        </button>
      </div>
    </div>
  );
}
