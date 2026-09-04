import { http, setCsrfToken } from '@/lib/http';

export interface UserProfile {
  publicId: string;
  username: string;
  displayName: string;
  department?: string;
  roles: string[];
  mustChangePassword: boolean;
}

export interface LoginPayload {
  username: string;
  password: string;
  captchaId?: string;
  captchaCode?: string;
}

export interface CaptchaResponse {
  enabled: boolean;
  captchaId?: string;
  image?: string;
}

export interface ChangePasswordPayload {
  oldPassword: string;
  newPassword: string;
}

export const authApi = {
  async getCsrf(): Promise<string> {
    const res = await http.get<{ data: { token: string } }>('/auth/csrf');
    const token = res.data.data.token;
    setCsrfToken(token);
    return token;
  },

  async getCaptcha(): Promise<CaptchaResponse> {
    const res = await http.get<{ data: CaptchaResponse }>('/auth/captcha');
    return res.data.data;
  },

  async login(payload: LoginPayload): Promise<UserProfile> {
    // Refresh/obtain CSRF token before login attempt
    try {
      await authApi.getCsrf();
    } catch {
      // If CSRF fetch fails in test, continue
    }
    const res = await http.post<{ data: UserProfile }>('/auth/login', payload);
    // Refresh CSRF after login session rotation
    try {
      await authApi.getCsrf();
    } catch {
      // ignore
    }
    return res.data.data;
  },

  async logout(): Promise<void> {
    try {
      await http.post('/auth/logout');
    } finally {
      setCsrfToken(null);
    }
  },

  async getCurrentUser(): Promise<UserProfile> {
    const res = await http.get<{ data: UserProfile }>('/auth/me');
    return res.data.data;
  },

  async changePassword(payload: ChangePasswordPayload): Promise<void> {
    await http.post('/auth/change-password', payload);
  },
};
