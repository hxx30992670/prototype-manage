import axios, { AxiosError } from 'axios';

type AuthEventListener = () => void;

class AuthEventEmitter {
  private listeners: AuthEventListener[] = [];

  subscribe(listener: AuthEventListener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  emitExpired() {
    this.listeners.forEach((listener) => listener());
  }
}

export const authEvents = new AuthEventEmitter();

// In-memory CSRF token (never persisted to localStorage)
let inMemoryCsrfToken: string | null = null;

export const setCsrfToken = (token: string | null) => {
  inMemoryCsrfToken = token;
};

export const getCsrfToken = () => inMemoryCsrfToken;

export interface NormalizedError {
  code: string;
  message: string;
  fieldErrors?: Array<{ field: string; message: string }>;
}

export const normalizeApiError = (error: unknown): NormalizedError => {
  if (axios.isAxiosError(error)) {
    const err = error as AxiosError<{
      code?: string;
      message?: string;
      fieldErrors?: Array<{ field: string; message: string }>;
    }>;
    if (err.response?.data) {
      return {
        code: err.response.data.code || 'UNKNOWN_ERROR',
        message: err.response.data.message || err.message,
        fieldErrors: err.response.data.fieldErrors,
      };
    }
    return {
      code: 'NETWORK_ERROR',
      message: err.message,
    };
  }
  if (isNormalizedError(error)) {
    return error;
  }
  return {
    code: 'UNKNOWN_ERROR',
    message: error instanceof Error ? error.message : '未知错误',
  };
};

function isNormalizedError(error: unknown): error is NormalizedError {
  return Boolean(
    error &&
      typeof error === 'object' &&
      !(error instanceof Error) &&
      'code' in error &&
      typeof (error as NormalizedError).code === 'string' &&
      'message' in error &&
      typeof (error as NormalizedError).message === 'string'
  );
}

export const http = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: {
    'X-Requested-With': 'XMLHttpRequest',
  },
});

const SAFE_METHODS = new Set(['get', 'head', 'options']);
let csrfInFlight: Promise<string | null> | null = null;

async function ensureCsrfToken(): Promise<string | null> {
  if (inMemoryCsrfToken) {
    return inMemoryCsrfToken;
  }
  if (!csrfInFlight) {
    csrfInFlight = http
      .get<{ data: { token: string } }>('/auth/csrf')
      .then((res) => {
        const token = res.data.data.token;
        inMemoryCsrfToken = token;
        return token;
      })
      .catch(() => null)
      .finally(() => {
        csrfInFlight = null;
      });
  }
  return csrfInFlight;
}

http.interceptors.request.use(async (config) => {
  const method = config.method?.toLowerCase();
  if (method && !SAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken();
    if (token) {
      config.headers['X-XSRF-TOKEN'] = token;
    }
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      authEvents.emitExpired();
    }
    return Promise.reject(normalizeApiError(error));
  }
);
