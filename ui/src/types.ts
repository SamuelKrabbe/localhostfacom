export interface Product {
  id: string;
  name: string;
  price: number;
  imageUrl?: string;
  active: boolean;
}

export interface CartItem extends Product {
  quantity: number;
}

export interface DashboardKPIs {
  totalRaised: number;
  totalOrders: number;
  averageTicket: number;
  topProduct: string;
}

export interface FundingGoal {
  current: number;
  target: number;
  crowdfundingUrl: string;
}

export interface ChartData {
  date: string;
  amount: number;
}

export interface Transaction {
  id: string;
  productNames: string; // Ex: "2x Café, 1x Bolo"
  amount: number;
  timestamp: string;
}

export interface DashboardResponse {
  kpis: DashboardKPIs;
  goal: FundingGoal;
  chartData: ChartData[];
  transactions: {
    content: Transaction[];
    totalPages: number;
  };
}

