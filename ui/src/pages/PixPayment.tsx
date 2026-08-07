import { useState, useEffect } from 'react';
import { Copy, CheckCircle, Loader2 } from 'lucide-react';

interface PixPaymentProps {
  orderId: string;
  pixCode: string;
  pixBase64: string;
  totalValue: number;
  onPaymentConfirmed: () => void;
}

export function PixPayment({ orderId, pixCode, pixBase64, totalValue, onPaymentConfirmed }: PixPaymentProps) {
  const [copied, setCopied] = useState(false);
  const [status, setStatus] = useState<'pending' | 'expired'>('pending');

  // Polling para checar status do pagamento
  useEffect(() => {
    if (status !== 'pending') return;

    const checkPaymentStatus = async () => {
      try {
        const response = await fetch(`/api/orders/${orderId}/status`);
        const data = await response.json();
        
        if (data.status === 'PAID') {
          onPaymentConfirmed();
        } else if (data.status === 'EXPIRED') {
          setStatus('expired');
        }
      } catch (error) {
        console.error('Erro ao checar status:', error);
      }
    };

    const interval = setInterval(checkPaymentStatus, 3000); // Checa a cada 3s
    
    // Timeout após 10 minutos (opcional, dependendo do backend)
    const timeout = setTimeout(() => setStatus('expired'), 10 * 60 * 1000);

    return () => {
      clearInterval(interval);
      clearTimeout(timeout);
    };
  }, [orderId, status, onPaymentConfirmed]);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(pixCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (status === 'expired') {
    return (
      <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center p-6 text-center">
        <p className="text-red-600 font-medium mb-2">O tempo para pagamento expirou.</p>
        <button onClick={() => window.location.reload()} className="mt-4 text-blue-600 underline">
          Tentar novamente
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 p-4 pb-24">
      <div className="max-w-md mx-auto bg-white rounded-xl shadow-sm border border-gray-200 p-6 flex flex-col items-center text-center">
        <h2 className="text-xl font-semibold text-gray-900 mb-2">Pagamento via Pix</h2>
        <p className="text-gray-600 mb-6">Escaneie o QR Code ou copie o código abaixo para pagar.</p>
        
        <div className="bg-gray-100 p-4 rounded-lg mb-6">
          <img src={`data:image/jpeg;base64,${pixBase64}`} alt="QR Code Pix" className="w-48 h-48" />
        </div>

        <div className="text-2xl font-bold text-gray-900 mb-6">
          {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(totalValue)}
        </div>

        <button 
          onClick={handleCopy}
          className="w-full flex items-center justify-center space-x-2 bg-gray-100 hover:bg-gray-200 text-gray-900 py-3 px-4 rounded-lg font-medium transition-colors mb-8"
        >
          {copied ? <CheckCircle size={20} className="text-green-600" /> : <Copy size={20} />}
          <span>{copied ? 'Código copiado!' : 'Copiar código Pix'}</span>
        </button>

        <div className="flex flex-col items-center text-gray-500 space-y-3">
          <Loader2 size={24} className="animate-spin text-blue-600" />
          <p className="text-sm">Aguardando confirmação do pagamento...</p>
        </div>
      </div>
    </div>
  );
}
