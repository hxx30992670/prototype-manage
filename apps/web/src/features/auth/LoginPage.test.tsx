import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { LoginPage } from './LoginPage';
import { authApi } from './api';

vi.mock('./api', () => ({
  authApi: {
    login: vi.fn(),
    getCsrf: vi.fn().mockResolvedValue('csrf-token'),
    getCaptcha: vi.fn().mockResolvedValue({ enabled: false }),
    getCurrentUser: vi.fn(),
  },
}));

function renderLoginPage(returnUrl?: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  const path = returnUrl ? `/login?returnUrl=${encodeURIComponent(returnUrl)}` : '/login';

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/prototypes" element={<div>原型列表页</div>} />
          <Route path="/change-password" element={<div>修改密码页</div>} />
          <Route path="/prototypes/01ABC" element={<div>原型详情页</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form elements and preserves return url', () => {
    renderLoginPage('/prototypes/01ABC');

    expect(screen.getByRole('heading', { name: '登录' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('用户名')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('密码')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '登 录' })).toBeInTheDocument();
    expect(screen.getByTestId('return-url')).toHaveValue('/prototypes/01ABC');
  });

  it('renders corporate branding and author signature in Chinese', () => {
    renderLoginPage();

    expect(screen.getByText('贵州戴玛科技')).toBeInTheDocument();
    expect(screen.getByText('贵州戴玛科技有限公司 · 原型资产管理平台')).toBeInTheDocument();
    expect(screen.getByText(/架构与设计：不忘初心 \(toms he\)/)).toBeInTheDocument();
    expect(screen.getByText('沙箱环境')).toBeInTheDocument();
    expect(screen.getByText('在线运行')).toBeInTheDocument();
  });

  it('successful login navigates to valid return url', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      publicId: '01USER',
      username: 'testuser',
      displayName: '测试用户',
      roles: ['CREATOR'],
      mustChangePassword: false,
    });

    renderLoginPage('/prototypes/01ABC');

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: '登 录' }));

    await waitFor(() => {
      expect(screen.getByText('原型详情页')).toBeInTheDocument();
    });
  });

  it('redirects to /change-password when login is blocked by PASSWORD_CHANGE_REQUIRED', async () => {
    vi.mocked(authApi.login).mockRejectedValue({
      code: 'PASSWORD_CHANGE_REQUIRED',
      message: '首次登录或重置密码后必须修改密码',
    });

    renderLoginPage();

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'admin' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: '登 录' }));

    await waitFor(() => {
      expect(screen.getByText('修改密码页')).toBeInTheDocument();
    });
  });

  it('redirects to /change-password if mustChangePassword is true', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      publicId: '01USER',
      username: 'testuser',
      displayName: '测试用户',
      roles: ['CREATOR'],
      mustChangePassword: true,
    });

    renderLoginPage('/prototypes');

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: '登 录' }));

    await waitFor(() => {
      expect(screen.getByText('修改密码页')).toBeInTheDocument();
    });
  });

  it('sanitizes external returnUrl and falls back to /prototypes', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      publicId: '01USER',
      username: 'testuser',
      displayName: '测试用户',
      roles: ['VIEWER'],
      mustChangePassword: false,
    });

    renderLoginPage('https://evil.com/phishing');

    fireEvent.change(screen.getByPlaceholderText('用户名'), { target: { value: 'testuser' } });
    fireEvent.change(screen.getByPlaceholderText('密码'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: '登 录' }));

    await waitFor(() => {
      expect(screen.getByText('原型列表页')).toBeInTheDocument();
    });
  });
});
