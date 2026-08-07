export interface Product {
  id: string;
  name: string;
  price: number;
  imageUrl: string | null;
  imageWidth: number | null;
  imageHeight: number | null;
  active: boolean;
}

export interface CartItem extends Product {
  quantity: number;
}

export interface OrderChargeResponse {
  orderId: string;
  total: number;
  payload: string;
  qrImageBase64: string;
  checkoutUrl: string | null;
  expiresAt: string;
}

export type OrderStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELED';

export interface OrderStatusResponse {
  status: OrderStatus;
  paidAt: string | null;
}

export interface DashboardKPIs {
  totalRaised: number;
  totalExpenses: number;
  /** Revenue minus expenses. Legitimately negative before sales cover the initial stock. */
  netBalance: number;
  totalOrders: number;
  averageTicket: number;
  /** Null until something sells. */
  topProduct: string | null;
  soldToday: number;
  soldThisWeek: number;
  soldThisMonth: number;
}

export interface FundingGoal {
  /** The net balance, so it can be negative. Clamp the progress bar, not this value. */
  current: number;
  target: number;
  crowdfundingUrl: string | null;
}

export interface ChartData {
  date: string;
  amount: number;
}

export interface Transaction {
  /** The order sequence, not the order UUID. */
  id: string;
  productNames: string;
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
    totalElements: number;
  };
}

export interface AdminUser {
  id: string;
  email: string;
  active: boolean;
  createdAt: string;
}

export interface AdminOrderItem {
  productName: string;
  unitPrice: number;
  quantity: number;
}

export interface AdminOrder {
  id: string;
  seq: number;
  status: OrderStatus;
  total: number;
  paymentProvider: string;
  hasCharge: boolean;
  createdAt: string;
  expiresAt: string;
  paidAt: string | null;
  paidManuallyBy: string | null;
  items: AdminOrderItem[];
}

export interface Expense {
  id: string;
  description: string;
  amount: number;
  incurredOn: string;
}

export interface Settings {
  goalTarget: number;
  crowdfundingUrl: string | null;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
}
