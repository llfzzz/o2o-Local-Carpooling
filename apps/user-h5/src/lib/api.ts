import { useSession } from './session';
import type { RefreshResponse, Session } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080';

type ApiErrorBody = { errorCode?: string; message?: string; traceId?: string };

export class ApiRequestError extends Error {
  readonly status: number;
  readonly errorCode?: string;
  readonly traceId?: string;
  constructor(status: number, body: ApiErrorBody) {
    super(body.message ?? `HTTP ${status}`);
    this.name = 'ApiRequestError';
    this.status = status;
    this.errorCode = body.errorCode;
    this.traceId = body.traceId;
  }
}

export async function rawApi<T>(path: string, options: { method?: string; token?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  if (!response.ok) {
    let body: ApiErrorBody = {};
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      // non-JSON error body — keep an empty shape
    }
    throw new ApiRequestError(response.status, body);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

// Authenticated request: attaches the access token and transparently refreshes once on 401.
export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const { session, setSession } = useSession.getState();
  if (!session) {
    throw new ApiRequestError(401, { errorCode: 'NO_SESSION', message: '未登录' });
  }
  try {
    return await rawApi<T>(path, { ...options, token: session.accessToken });
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      try {
        const refreshed = await rawApi<RefreshResponse>('/api/auth/refresh', {
          method: 'POST',
          body: { refreshToken: session.refreshToken }
        });
        setSession({ accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken, user: session.user });
        return await rawApi<T>(path, { ...options, token: refreshed.accessToken });
      } catch {
        setSession(null);
      }
    }
    throw error;
  }
}

export function describeError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const parts = [error.message];
    if (error.errorCode) parts.push(error.errorCode);
    if (error.traceId) parts.push(`trace ${error.traceId.slice(0, 8)}`);
    return parts.join(' · ');
  }
  return error instanceof Error ? error.message : String(error);
}

export function logout(session: Session, setSession: (session: Session | null) => void) {
  void rawApi('/api/auth/logout', { method: 'POST', body: { refreshToken: session.refreshToken } }).catch(() => undefined);
  setSession(null);
}
