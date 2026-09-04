import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RequireAuth } from './RequireAuth';
import { LoginPage } from '@/features/auth/LoginPage';
import { authApi } from '@/features/auth/api';

vi.mock('@/features/auth/api', () => ({
  authApi: {
    getCurrentUser: vi.fn(),
    getCsrf: vi.fn().mockResolvedValue('csrf-token'),
    login: vi.fn(),
  },
}));

function renderRouter(initialPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route path="/prototypes/01ABC" element={<div>原型详情页</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('redirects unauthenticated users to login and preserves return url', async () => {
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(new Error('Unauthorized'));

    renderRouter('/prototypes/01ABC');

    expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
    expect(screen.getByTestId('return-url')).toHaveValue('/prototypes/01ABC');
  });

  it('uses the cached login profile even if a previous /me request failed', async () => {
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(new Error('Unauthorized'));
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    queryClient.setQueryData(['auth', 'me'], {
      publicId: '01TEST',
      username: 'tester',
      displayName: '测试人员',
      roles: ['VIEWER'],
      mustChangePassword: false,
    });

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/prototypes/01ABC']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireAuth />}>
              <Route path="/prototypes/01ABC" element={<div>原型详情页</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );

    expect(await screen.findByText('原型详情页')).toBeInTheDocument();
  });

  it('allows authenticated users to view protected page', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValue({
      publicId: '01TEST',
      username: 'tester',
      displayName: '测试人员',
      roles: ['VIEWER'],
      mustChangePassword: false,
    });

    renderRouter('/prototypes/01ABC');

    expect(await screen.findByText('原型详情页')).toBeInTheDocument();
  });
});
