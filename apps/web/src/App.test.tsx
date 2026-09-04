import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { createMemoryRouter } from 'react-router-dom';
import App from './App';
import { adminRoutes } from '@/routes/router';

vi.mock('@/features/auth/api', () => ({
  authApi: {
    getCurrentUser: vi.fn().mockRejectedValue(new Error('Unauthenticated')),
    getCsrf: vi.fn().mockResolvedValue('csrf-token'),
    login: vi.fn(),
  },
}));

describe('App', () => {
  it('renders and redirects to login when unauthenticated', async () => {
    const memoryRouter = createMemoryRouter(adminRoutes, {
      initialEntries: ['/'],
    });

    render(<App router={memoryRouter} />);
    expect(await screen.findByRole('heading', { name: '登录' })).toBeInTheDocument();
  });
});
