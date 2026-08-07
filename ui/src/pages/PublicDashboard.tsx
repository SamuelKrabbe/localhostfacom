import { useState, useEffect } from 'react';
import { 
  DollarSign, 
  ShoppingBag, 
  TrendingUp, 
  Award, 
  ExternalLink,
  Loader2
} from 'lucide-react';
import { 
  BarChart, 
  Bar, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts';
import type { DashboardResponse } from '../types';

// Tipagens baseadas no que a API REST deve retornar
export function PublicDashboard() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [page, setPage] = useState(0);

  // Busca os dados da API (KPIs, Gráfico e primeira página de transações)
  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setIsLoading(true);
        // Exemplo de endpoint REST: /api/public/dashboard?page={page}
        const response = await fetch(`/api/public/dashboard?page=${page}`);
        const result = await response.json();
        
        setData(prev => {
          if (!prev || page === 0) return result;
          // Se estiver paginando, adiciona as novas transações à lista existente
          return {
            ...result,
            transactions: {
              ...result.transactions,
              content: [...prev.transactions.content, ...result.transactions.content]
            }
          };
        });
      } catch (error) {
        console.error('Erro ao carregar dashboard:', error);
      } finally {
        setIsLoading(false);
      }
    };

    fetchDashboardData();
  }, [page]);

  const formatCurrency = (value: number) => 
    new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

  const formatDate = (isoString: string) => 
    new Intl.DateTimeFormat('pt-BR', { 
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' 
    }).format(new Date(isoString));

  if (isLoading && !data) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-blue-600" />
      </div>
    );
  }

  if (!data) return null;

  const progressPercentage = Math.min((data.goal.current / data.goal.target) * 100, 100);

  return (
    <div className="min-h-screen bg-gray-50 p-4 md:p-8">
      <div className="max-w-6xl mx-auto space-y-6">
        
        {/* Cabeçalho */}
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Portal de Transparência</h1>
          <p className="text-gray-600">Acompanhe a arrecadação da nossa sala de estudos em tempo real.</p>
        </div>

        {/* Meta de Arrecadação */}
        <a 
          href={data.goal.crowdfundingUrl} 
          target="_blank" 
          rel="noopener noreferrer"
          className="block bg-white p-6 rounded-xl border border-gray-200 shadow-sm hover:border-blue-300 transition-colors group cursor-pointer"
        >
          <div className="flex justify-between items-end mb-2">
            <div>
              <h2 className="text-sm font-medium text-gray-600 mb-1">Meta de Arrecadação</h2>
              <div className="flex items-baseline space-x-2">
                <span className="text-2xl font-bold text-gray-900">{formatCurrency(data.goal.current)}</span>
                <span className="text-sm text-gray-500">de {formatCurrency(data.goal.target)}</span>
              </div>
            </div>
            <div className="flex items-center text-blue-600 text-sm font-medium">
              <span>Ajudar na Vaquinha</span>
              <ExternalLink size={16} className="ml-1 group-hover:translate-x-1 transition-transform" />
            </div>
          </div>
          <div className="w-full bg-gray-100 rounded-full h-3 overflow-hidden">
            <div 
              className="bg-blue-600 h-3 rounded-full transition-all duration-1000 ease-out"
              style={{ width: `${progressPercentage}%` }}
            />
          </div>
          <p className="text-right text-xs text-gray-500 mt-2">{progressPercentage.toFixed(1)}% alcançado</p>
        </a>

        {/* KPIs Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex items-start space-x-4">
            <div className="bg-green-100 p-3 rounded-lg text-green-700">
              <DollarSign size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-600 font-medium">Total Arrecadado</p>
              <p className="text-xl font-bold text-gray-900">{formatCurrency(data.kpis.totalRaised)}</p>
            </div>
          </div>

          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex items-start space-x-4">
            <div className="bg-blue-100 p-3 rounded-lg text-blue-700">
              <ShoppingBag size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-600 font-medium">Pedidos Realizados</p>
              <p className="text-xl font-bold text-gray-900">{data.kpis.totalOrders}</p>
            </div>
          </div>

          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex items-start space-x-4">
            <div className="bg-purple-100 p-3 rounded-lg text-purple-700">
              <TrendingUp size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-600 font-medium">Ticket Médio</p>
              <p className="text-xl font-bold text-gray-900">{formatCurrency(data.kpis.averageTicket)}</p>
            </div>
          </div>

          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex items-start space-x-4">
            <div className="bg-orange-100 p-3 rounded-lg text-orange-700">
              <Award size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-600 font-medium">Mais Vendido</p>
              <p className="text-xl font-bold text-gray-900 line-clamp-1" title={data.kpis.topProduct}>
                {data.kpis.topProduct}
              </p>
            </div>
          </div>
        </div>

        {/* Gráfico e Tabela de Transações (Grid Responsivo) */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          
          {/* Gráfico de Vendas */}
          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
            <h3 className="text-lg font-semibold text-gray-900 mb-6">Arrecadação nos Últimos 7 Dias</h3>
            <div className="h-64 w-full">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={data.chartData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                  <XAxis 
                    dataKey="date" 
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 12, fill: '#6B7280' }}
                    dy={10}
                  />
                  <YAxis 
                    axisLine={false}
                    tickLine={false}
                    tick={{ fontSize: 12, fill: '#6B7280' }}
                    tickFormatter={(val) => `R$${val}`}
                  />
                  <Tooltip 
                    cursor={{ fill: '#F3F4F6' }}
                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)' }}
                    formatter={(value: number) => [formatCurrency(value), 'Arrecadado']}
                    labelStyle={{ color: '#374151', fontWeight: 'bold', marginBottom: '4px' }}
                  />
                  <Bar dataKey="amount" fill="#2563EB" radius={[4, 4, 0, 0]} maxBarSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          {/* Últimas Transações */}
          <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex flex-col">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">Últimas Contribuições</h3>
            
            <div className="flex-1 overflow-y-auto min-h-[250px]">
              {data.transactions.content.length === 0 ? (
                <p className="text-center text-gray-500 py-8">Nenhuma contribuição registrada ainda.</p>
              ) : (
                <ul className="divide-y divide-gray-100">
                  {data.transactions.content.map((tx) => (
                    <li key={tx.id} className="py-3 flex justify-between items-center">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{tx.productNames}</p>
                        <p className="text-xs text-gray-500">{formatDate(tx.timestamp)}</p>
                      </div>
                      <span className="text-sm font-semibold text-green-600">
                        +{formatCurrency(tx.amount)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {/* Paginação */}
            {page < data.transactions.totalPages - 1 && (
              <button 
                onClick={() => setPage(p => p + 1)}
                disabled={isLoading}
                className="mt-4 w-full py-2 text-sm font-medium text-blue-600 bg-blue-50 rounded-lg hover:bg-blue-100 transition-colors disabled:opacity-50"
              >
                {isLoading ? 'Carregando...' : 'Carregar mais'}
              </button>
            )}
          </div>

        </div>
      </div>
    </div>
  );
}
