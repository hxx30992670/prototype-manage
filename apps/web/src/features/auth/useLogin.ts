import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi, type CaptchaResponse, type LoginPayload } from './api';
import { normalizeApiError } from '@/lib/http';

export function sanitizeReturnUrl(url: string | null): string {
  if (!url) return '/prototypes';
  if (url.startsWith('/') && !url.startsWith('//')) {
    return url;
  }
  return '/prototypes';
}

export function useLogin() {
  const [searchParams] = useSearchParams();
  const rawReturnUrl = searchParams.get('returnUrl');
  const returnUrl = rawReturnUrl || '/prototypes';
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const captchaQuery = useQuery({
    queryKey: ['auth', 'captcha'],
    queryFn: async (): Promise<CaptchaResponse> => {
      try {
        return await authApi.getCaptcha();
      } catch {
        return { enabled: false };
      }
    },
  });

  const captcha = captchaQuery.data ?? { enabled: false };
  const loadCaptcha = () => {
    void captchaQuery.refetch();
  };

  const handleSubmit = async (values: LoginPayload) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const user = await authApi.login({
        ...values,
        captchaId: captcha.captchaId,
        captchaCode: values.captchaCode,
      });
      queryClient.setQueryData(['auth', 'me'], user);

      if (user.mustChangePassword) {
        navigate('/change-password');
      } else {
        navigate(sanitizeReturnUrl(rawReturnUrl));
      }
    } catch (err) {
      const normalized = normalizeApiError(err);
      if (normalized.code === 'PASSWORD_CHANGE_REQUIRED') {
        navigate('/change-password');
        return;
      }
      setErrorMessage(normalized.message || '登录失败，请重试');
      if (captcha.enabled) {
        void loadCaptcha();
      }
    } finally {
      setLoading(false);
    }
  };

  return {
    loading,
    errorMessage,
    captcha,
    returnUrl,
    loadCaptcha,
    handleSubmit,
  };
}
