import { request, upload } from './client';
import type {
  AdminOrder,
  AdminUser,
  Expense,
  OrderStatus,
  Page,
  Product,
  Settings,
} from '../types';

export function me(): Promise<AdminUser> {
  return request<AdminUser>('/admin/me', { auth: true });
}

// Products

export interface ProductInput {
  name: string;
  price: number;
  imageId?: string | null;
  active?: boolean;
}

export function listProducts(): Promise<Product[]> {
  return request<Product[]>('/admin/products', { auth: true });
}

export function createProduct(input: ProductInput): Promise<Product> {
  return request<Product>('/admin/products', { method: 'POST', body: input, auth: true });
}

export function updateProduct(id: string, input: ProductInput): Promise<Product> {
  return request<Product>(`/admin/products/${id}`, { method: 'PUT', body: input, auth: true });
}

export function deleteProduct(id: string): Promise<void> {
  return request<void>(`/admin/products/${id}`, { method: 'DELETE', auth: true });
}

// Images

export interface UploadedImage {
  id: string;
  url: string;
  width: number;
  height: number;
}

export function uploadImage(file: File): Promise<UploadedImage> {
  return upload<UploadedImage>('/admin/images', file);
}

export function deleteImage(id: string): Promise<void> {
  return request<void>(`/admin/images/${id}`, { method: 'DELETE', auth: true });
}

// Orders

export function listOrders(status?: OrderStatus, page = 0, size = 20): Promise<Page<AdminOrder>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) {
    query.set('status', status);
  }
  return request<Page<AdminOrder>>(`/admin/orders?${query}`, { auth: true });
}

/** Used when the webhook never arrived but the money plainly did. */
export function markOrderPaid(id: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/mark-paid`, { method: 'POST', auth: true });
}

export function syncOrder(id: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/sync`, { method: 'POST', auth: true });
}

export function cancelOrder(id: string): Promise<AdminOrder> {
  return request<AdminOrder>(`/admin/orders/${id}/cancel`, { method: 'POST', auth: true });
}

// Expenses

export interface ExpenseInput {
  description: string;
  amount: number;
  incurredOn?: string;
}

export function listExpenses(): Promise<Expense[]> {
  return request<Expense[]>('/admin/expenses', { auth: true });
}

export function createExpense(input: ExpenseInput): Promise<Expense> {
  return request<Expense>('/admin/expenses', { method: 'POST', body: input, auth: true });
}

export function deleteExpense(id: string): Promise<void> {
  return request<void>(`/admin/expenses/${id}`, { method: 'DELETE', auth: true });
}

// Settings

export function getSettings(): Promise<Settings> {
  return request<Settings>('/admin/settings', { auth: true });
}

export function updateSettings(input: Settings): Promise<Settings> {
  return request<Settings>('/admin/settings', { method: 'PUT', body: input, auth: true });
}

// Admins

export function listAdmins(): Promise<AdminUser[]> {
  return request<AdminUser[]>('/admin/admins', { auth: true });
}

export function createAdmin(email: string, password: string): Promise<AdminUser> {
  return request<AdminUser>('/admin/admins', {
    method: 'POST',
    body: { email, password },
    auth: true,
  });
}

export function deleteAdmin(id: string): Promise<void> {
  return request<void>(`/admin/admins/${id}`, { method: 'DELETE', auth: true });
}
