import { request, tokenStorage } from './client';

export interface LoginResponse {
  token: string;
  email: string;
  expiresAt: string;
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const response = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    body: { email, password },
  });
  tokenStorage.set(response.token);
  return response;
}

export function logout(): void {
  tokenStorage.clear();
}

export function isLoggedIn(): boolean {
  return tokenStorage.get() !== null;
}
