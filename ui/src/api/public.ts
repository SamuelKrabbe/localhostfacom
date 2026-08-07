import { request } from './client';
import type {
  DashboardResponse,
  OrderChargeResponse,
  OrderStatusResponse,
  Product,
} from '../types';

export function listProducts(): Promise<Product[]> {
  return request<Product[]>('/public/products');
}

export interface OrderItemInput {
  productId: string;
  quantity: number;
}

/**
 * Sends quantities only. The API recomputes the total from its own prices, so there is
 * nothing here for a tampered client to inflate.
 */
export function createOrder(items: OrderItemInput[]): Promise<OrderChargeResponse> {
  return request<OrderChargeResponse>('/public/orders', { method: 'POST', body: { items } });
}

/**
 * Retries charge creation for an order that already exists. Idempotent, so calling it
 * after a provider hiccup returns the original charge rather than a second payable one.
 */
export function createCharge(orderId: string): Promise<OrderChargeResponse> {
  return request<OrderChargeResponse>(`/public/orders/${orderId}/charge`, { method: 'POST' });
}

export function getOrderStatus(orderId: string, signal?: AbortSignal): Promise<OrderStatusResponse> {
  return request<OrderStatusResponse>(`/public/orders/${orderId}/status`, { signal });
}

export function getDashboard(page = 0, size = 20): Promise<DashboardResponse> {
  return request<DashboardResponse>(`/public/dashboard?page=${page}&size=${size}`);
}
