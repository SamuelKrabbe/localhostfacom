const BASE_URL = import.meta.env.VITE_API_URL ?? '/api';
const TOKEN_KEY = 'localhostfacom.token';

/** Mirrors the RFC 7807 problem+json body the API returns. */
export class ApiError extends Error {
  status: number;
  slug: string;
  fieldErrors?: Record<string, string>;
  orderId?: string;

  constructor(
    status: number,
    slug: string,
    message: string,
    fieldErrors?: Record<string, string>,
    orderId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.slug = slug;
    this.fieldErrors = fieldErrors;
    this.orderId = orderId;
  }
}

export const tokenStorage = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string): void => localStorage.setItem(TOKEN_KEY, token),
  clear: (): void => localStorage.removeItem(TOKEN_KEY),
};

interface RequestOptions {
  method?: string;
  body?: unknown;
  auth?: boolean;
  signal?: AbortSignal;
}

async function toApiError(response: Response): Promise<ApiError> {
  let slug = 'unknown-error';
  let detail = `Request failed with status ${response.status}`;
  let fieldErrors: Record<string, string> | undefined;
  let orderId: string | undefined;

  try {
    const problem = await response.json();
    slug = problem.slug ?? slug;
    detail = problem.detail ?? detail;
    fieldErrors = problem.errors;
    orderId = problem.orderId;
  } catch {
    // A non-JSON body (a proxy error page, say) leaves the defaults in place.
  }

  return new ApiError(response.status, slug, detail, fieldErrors, orderId);
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = false, signal } = options;
  const headers: Record<string, string> = {};

  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (auth) {
    const token = tokenStorage.get();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    signal,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && auth) {
    // The token is gone or the admin was deactivated; stop replaying a dead token.
    tokenStorage.clear();
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/** Multipart upload; the browser sets its own Content-Type boundary. */
export async function upload<T>(path: string, file: File): Promise<T> {
  const form = new FormData();
  form.append('file', file);

  const token = tokenStorage.get();
  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await response.json()) as T;
}
