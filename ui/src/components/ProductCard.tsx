import { Plus, Minus } from 'lucide-react';
import type { Product } from '../types';

interface ProductCardProps {
  product: Product;
  quantity: number;
  onUpdateQuantity: (id: string, delta: number) => void;
}

export function ProductCard({ product, quantity, onUpdateQuantity }: ProductCardProps) {
  return (
    <div className="flex items-center justify-between p-4 bg-white border border-gray-200 rounded-lg shadow-sm">
      <div className="flex items-center space-x-4">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} className="w-16 h-16 object-cover rounded-md bg-gray-100" />
        ) : (
          <div className="w-16 h-16 bg-gray-100 rounded-md flex items-center justify-center text-gray-400">
            <span className="text-xs text-center px-1">Sem foto</span>
          </div>
        )}
        <div>
          <h3 className="text-base font-medium text-gray-900">{product.name}</h3>
          <p className="text-sm text-gray-600">
            {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(product.price)}
          </p>
        </div>
      </div>

      <div className="flex items-center space-x-3 bg-gray-50 p-1 rounded-lg border border-gray-200">
        <button 
          onClick={() => onUpdateQuantity(product.id, -1)}
          className="p-1 text-gray-600 hover:text-gray-900 disabled:opacity-50"
          disabled={quantity === 0}
        >
          <Minus size={18} />
        </button>
        <span className="w-6 text-center font-medium text-gray-900">{quantity}</span>
        <button 
          onClick={() => onUpdateQuantity(product.id, 1)}
          className="p-1 text-gray-600 hover:text-gray-900"
        >
          <Plus size={18} />
        </button>
      </div>
    </div>
  );
}
