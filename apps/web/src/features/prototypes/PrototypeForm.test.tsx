import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PrototypeForm } from './PrototypeForm';
import { prototypeApi, type PrototypeItem } from './api';
import { authApi } from '@/features/auth/api';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    prototypeApi: {
      ...actual.prototypeApi,
      listCategories: vi.fn(),
      listTags: vi.fn(),
      listAssignableOwners: vi.fn(),
      listActiveUsers: vi.fn(),
      update: vi.fn(),
    },
  };
});

vi.mock('@/features/auth/api', () => ({ authApi: { getCurrentUser: vi.fn() } }));

const owner = { publicId: 'owner', username: 'creator', displayName: '负责人' };
const viewer = { publicId: 'viewer', username: 'viewer', displayName: '下载人员' };
const currentUser = { ...owner, roles: ['CREATOR'], mustChangePassword: false };
const prototype: PrototypeItem = {
  publicId: 'proto-1',
  code: 'demo',
  name: '受限原型',
  category: { code: 'finance', name: '金融业务' },
  createdBy: owner,
  owner,
  owners: [owner],
  visibility: 'RESTRICTED',
  viewers: [viewer],
  downloadAccess: 'SELECTED',
  downloaders: [viewer],
  reviewStatus: 'DRAFT',
  archived: false,
  tags: [],
  rowVersion: 1,
  createdAt: '2026-10-08T00:00:00Z',
  updatedAt: '2026-10-08T00:00:00Z',
};

function renderForm(cachedMembers: boolean) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(['auth', 'me'], currentUser);
  if (cachedMembers) queryClient.setQueryData(['users', 'active'], [owner, viewer]);

  const content = (open: boolean) => (
    <QueryClientProvider client={queryClient}>
      <PrototypeForm open={open} prototype={prototype} onClose={vi.fn()} />
    </QueryClientProvider>
  );
  const view = render(content(true));
  return { ...view, setOpen: (open: boolean) => view.rerender(content(open)) };
}

async function saveForm() {
  fireEvent.click(screen.getByRole('button', { name: /保.*存/ }));
  await waitFor(() => expect(prototypeApi.update).toHaveBeenCalledTimes(1));
  return vi.mocked(prototypeApi.update).mock.calls[0][1];
}

describe('PrototypeForm download permissions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(prototypeApi.listCategories).mockResolvedValue([{ code: 'finance', name: '金融业务', sortNo: 1 }]);
    vi.mocked(prototypeApi.listTags).mockResolvedValue([]);
    vi.mocked(prototypeApi.listAssignableOwners).mockResolvedValue([owner]);
    vi.mocked(prototypeApi.listActiveUsers).mockResolvedValue([owner, viewer]);
    vi.mocked(prototypeApi.update).mockResolvedValue(prototype);
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(currentUser);
  });

  afterEach(cleanup);

  it.each([false, true])('preserves selected downloaders when editing with cached members: %s', async (cachedMembers) => {
    renderForm(cachedMembers);
    const nameInput = await screen.findByLabelText('原型名称');
    await screen.findByLabelText('可下载的人');
    await waitFor(() => expect(prototypeApi.listAssignableOwners).toHaveBeenCalled());
    fireEvent.change(nameInput, { target: { value: '修改后的原型名称' } });

    const payload = await saveForm();
    expect(payload.name).toBe('修改后的原型名称');
    expect(payload.viewerIds).toEqual([viewer.publicId]);
    expect(payload.downloaderIds).toEqual([viewer.publicId]);
  });

  it('removes download permission when a selected viewer is explicitly removed', async () => {
    renderForm(true);
    const viewerInput = await screen.findByLabelText('可查看的人');
    const downloaderSelect = (await screen.findByLabelText('可下载的人')).closest('.ant-select');
    await waitFor(() => expect(within(downloaderSelect as HTMLElement).getByText('下载人员 (viewer)')).toBeInTheDocument());
    const viewerSelect = viewerInput.closest('.ant-select');
    expect(viewerSelect).not.toBeNull();
    fireEvent.click(within(viewerSelect as HTMLElement).getByLabelText('close'));

    const payload = await saveForm();
    expect(payload.viewerIds).toEqual([]);
    expect(payload.downloaderIds).toEqual([]);
  });

  it('preserves selected downloaders after closing and reopening the form', async () => {
    const { setOpen } = renderForm(true);
    await screen.findByLabelText('可下载的人');
    setOpen(false);
    await waitFor(() => expect(screen.queryByLabelText('可下载的人')).not.toBeInTheDocument());
    setOpen(true);
    await screen.findByLabelText('可下载的人');

    const payload = await saveForm();
    expect(payload.downloaderIds).toEqual([viewer.publicId]);
  });
});
