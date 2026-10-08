import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PrototypeListPage } from './PrototypeListPage';
import { prototypeApi } from './api';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    prototypeApi: {
      ...actual.prototypeApi,
      list: vi.fn(),
      listCategories: vi.fn().mockResolvedValue([{ code: 'finance', name: '金融业务', sortNo: 1 }]),
      listTags: vi.fn().mockResolvedValue([{ name: 'React', color: '#1677ff' }]),
      listAssignableOwners: vi.fn().mockResolvedValue([]),
      listActiveUsers: vi.fn().mockResolvedValue([]),
      archive: vi.fn(),
      updateReviewStatus: vi.fn(),
      delete: vi.fn(),
    },
  };
});

vi.mock('@/features/auth/api', () => ({
  authApi: {
    getCurrentUser: vi.fn().mockResolvedValue({
      publicId: '01USER00000000000000000001',
      username: 'creator',
      displayName: '设计人员',
      roles: ['CREATOR'],
      mustChangePassword: false,
    }),
  },
}));

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PrototypeListPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PrototypeListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('sends pagination and filters and renders empty draft state', async () => {
    vi.mocked(prototypeApi.list).mockResolvedValue({
      data: [
        {
          publicId: '01PROTO0000000000000000001',
          code: 'draft-proto',
          name: '草稿原型',
          visibility: 'ALL_INTERNAL',
          reviewStatus: 'DRAFT',
          archived: false,
          category: { code: 'finance', name: '金融业务' },
          createdBy: { publicId: '01USER00000000000000000001', username: 'creator', displayName: '设计人员' },
          owner: { publicId: '01USER00000000000000000001', username: 'creator', displayName: '设计人员' },
          tags: [],
          rowVersion: 1,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ],
      pagination: {
        page: 1,
        pageSize: 20,
        total: 1,
        totalPages: 1,
      },
    });

    renderPage();

    fireEvent.change(screen.getByLabelText('评审状态'), { target: { value: 'DRAFT' } });

    expect(await screen.findByText('尚未发布版本')).toBeInTheDocument();
    const nameLink = screen.getByRole('link', { name: '草稿原型' });
    expect(nameLink).toHaveAttribute('href', '/prototypes/01PROTO0000000000000000001');
    expect(screen.getByText('草稿原型')).toBeInTheDocument();

    await waitFor(() => {
      expect(prototypeApi.list).toHaveBeenCalledWith(
        expect.objectContaining({
          reviewStatus: 'DRAFT',
          page: 1,
          pageSize: 20,
        })
      );
    });
  });
});
