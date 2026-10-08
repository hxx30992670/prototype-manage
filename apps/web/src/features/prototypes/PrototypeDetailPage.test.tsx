import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PrototypeDetailPage } from './PrototypeDetailPage';
import { canDownloadPrototype, prototypeApi } from './api';
import { versionApi } from '@/features/versions/api';

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api')>();
  return {
    ...actual,
    prototypeApi: {
      ...actual.prototypeApi,
      get: vi.fn(),
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

vi.mock('@/features/versions/api', () => ({
  versionApi: {
    list: vi.fn().mockResolvedValue([]),
  },
  versionKeys: {
    list: (id: string) => ['versions', 'list', id] as const,
  },
}));

vi.mock('@/features/versions/VersionList', () => ({
  VersionList: () => <div>版本历史记录</div>,
}));

vi.mock('@/features/versions/PrototypePreview', () => ({
  PrototypePreview: () => <div>原型预览沙箱</div>,
}));

vi.mock('@/features/specs/PrototypeSpecEditor', () => ({
  PrototypeSpecEditor: () => <div>原型结构化说明与约束</div>,
}));

vi.mock('@/features/shares/ShareSettings', () => ({
  ShareSettings: () => <div>免登录对外分享链接</div>,
}));

vi.mock('@/features/comments/CommentThread', () => ({
  CommentThread: () => <div>评论</div>,
}));

vi.mock('@/features/attachments/api', () => ({
  attachmentApi: {
    listForPrototype: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock('@/features/attachments/AttachmentList', () => ({
  AttachmentList: () => <div>暂无附件资源</div>,
}));

function renderDetail() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/prototypes/01PROTO0000000000000000001']}>
        <Routes>
          <Route path="/prototypes/:id" element={<PrototypeDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('PrototypeDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(versionApi.list).mockResolvedValue([]);
    vi.mocked(prototypeApi.get).mockResolvedValue({
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
    });
  });

  it('renders version, spec and share management tabs', async () => {
    renderDetail();

    expect((await screen.findAllByText('草稿原型')).length).toBeGreaterThan(0);
    expect(screen.getByRole('tab', { name: '版本' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '说明与约束' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '分享设置' })).toBeInTheDocument();
    expect(screen.getByText('版本历史记录')).toBeInTheDocument();
    expect(screen.getByText('全员可见')).toBeInTheDocument();
    expect(screen.getByText('仅负责人、创建者和管理员')).toBeInTheDocument();
  });

  it('shows the people allowed to view a restricted prototype', async () => {
    vi.mocked(prototypeApi.get).mockResolvedValue({
      publicId: '01PROTO0000000000000000001',
      code: 'draft-proto',
      name: '草稿原型',
      visibility: 'RESTRICTED',
      reviewStatus: 'DRAFT',
      archived: false,
      category: { code: 'finance', name: '金融业务' },
      createdBy: { publicId: '01USER00000000000000000001', username: 'creator', displayName: '设计人员' },
      owner: { publicId: '01USER00000000000000000001', username: 'creator', displayName: '设计人员' },
      viewers: [{ publicId: '01USER00000000000000000002', username: 'viewer', displayName: '普通查看者' }],
      downloadAccess: 'SELECTED',
      downloaders: [{ publicId: '01USER00000000000000000002', username: 'viewer', displayName: '普通查看者' }],
      tags: [],
      rowVersion: 1,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    renderDetail();

    expect(await screen.findByText('受限访问')).toBeInTheDocument();
    expect(screen.getByText('普通查看者')).toBeInTheDocument();
    expect(screen.getByText('指定：普通查看者')).toBeInTheDocument();
  });

  it('shows publishing hint on preview tab while first version is still publishing', async () => {
    vi.mocked(versionApi.list).mockResolvedValue([
      {
        versionId: 'ver-1',
        versionNo: 1,
        changeLog: '首个版本',
        status: 'PUBLISHING',
        sourceType: 'ZIP',
        sourceSize: 1024,
        fileCount: 0,
        expandedSize: 0,
        isCurrent: false,
        createdAt: new Date().toISOString(),
      },
    ]);

    renderDetail();
    fireEvent.click(await screen.findByRole('tab', { name: '预览' }));

    expect(await screen.findByText('版本正在发布中，完成后即可预览')).toBeInTheDocument();
  });

  it('renders preview for the current published version', async () => {
    vi.mocked(versionApi.list).mockResolvedValue([
      {
        versionId: 'ver-2',
        versionNo: 2,
        changeLog: '当前版本',
        status: 'PUBLISHED',
        sourceType: 'ZIP',
        sourceSize: 2048,
        fileCount: 3,
        expandedSize: 4096,
        isCurrent: true,
        createdAt: new Date().toISOString(),
      },
    ]);

    renderDetail();
    fireEvent.click(await screen.findByRole('tab', { name: '预览' }));

    expect(await screen.findByText('原型预览沙箱')).toBeInTheDocument();
  });
});

describe('canDownloadPrototype', () => {
  const viewer = { publicId: '01USER00000000000000000002', roles: ['VIEWER'] };
  const prototype = {
    visibility: 'RESTRICTED' as const,
    createdBy: { publicId: '01USER00000000000000000001', username: 'creator', displayName: '设计人员' },
    owner: { publicId: '01USER00000000000000000001', username: 'creator', displayName: '设计人员' },
    viewers: [{ publicId: viewer.publicId, username: 'viewer', displayName: '普通查看者' }],
    downloaders: [{ publicId: viewer.publicId, username: 'viewer', displayName: '普通查看者' }],
  };

  it('keeps source download with managers until a wider policy is set', () => {
    expect(canDownloadPrototype({ ...prototype, downloadAccess: 'MANAGERS_ONLY' }, viewer)).toBe(false);
    expect(canDownloadPrototype(
      { ...prototype, downloadAccess: 'MANAGERS_ONLY' },
      { publicId: '01USER00000000000000000001', roles: ['CREATOR'] },
    )).toBe(true);
  });

  it('lets every current viewer download when the policy is all visible people', () => {
    expect(canDownloadPrototype({ ...prototype, downloadAccess: 'ALL_VIEWERS' }, viewer)).toBe(true);
    expect(canDownloadPrototype({
      ...prototype,
      visibility: 'RESTRICTED',
      viewers: [],
      downloadAccess: 'ALL_VIEWERS',
    }, viewer)).toBe(false);
    expect(canDownloadPrototype({
      ...prototype,
      visibility: 'ALL_INTERNAL',
      downloadAccess: 'ALL_VIEWERS',
    }, viewer)).toBe(true);
  });

  it('lets only the selected people download, and only while they can still view', () => {
    expect(canDownloadPrototype({ ...prototype, downloadAccess: 'SELECTED' }, viewer)).toBe(true);
    expect(canDownloadPrototype({
      ...prototype,
      downloadAccess: 'SELECTED',
      viewers: [],
    }, viewer)).toBe(false);
    expect(canDownloadPrototype({
      ...prototype,
      visibility: 'ALL_INTERNAL',
      downloadAccess: 'SELECTED',
      downloaders: [],
    }, viewer)).toBe(false);
  });
});
