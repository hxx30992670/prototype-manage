import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { VersionList } from './VersionList';
import { versionApi } from './api';

vi.mock('./api', () => ({
  versionApi: {
    list: vi.fn(),
    getPublishJob: vi.fn(),
    switchCurrent: vi.fn(),
    createDownloadTicket: vi.fn(),
    delete: vi.fn(),
    createUpload: vi.fn(),
    directUpload: vi.fn(),
    completeUpload: vi.fn(),
    createVersion: vi.fn(),
  },
  versionKeys: {
    list: (id: string) => ['versions', 'list', id] as const,
  },
}));

function renderList(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe('VersionList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders version list and displays current version badge', async () => {
    vi.mocked(versionApi.list).mockResolvedValue([
      {
        versionId: 'ver-2',
        versionNo: 2,
        changeLog: '修复表单校验BUG',
        status: 'PUBLISHED',
        sourceType: 'ZIP',
        sourceSize: 2048,
        entryPath: 'index.html',
        fileCount: 5,
        expandedSize: 8192,
        isCurrent: true,
        createdBy: '张三',
        createdAt: '2026-09-01T12:00:00Z',
      },
      {
        versionId: 'ver-1',
        versionNo: 1,
        changeLog: '初始版本',
        status: 'PUBLISHED',
        sourceType: 'ZIP',
        sourceSize: 1024,
        entryPath: 'index.html',
        fileCount: 3,
        expandedSize: 4096,
        isCurrent: false,
        createdBy: '李四',
        createdAt: '2026-09-01T10:00:00Z',
      },
    ]);

    renderList(<VersionList prototypeId="proto-123" canManage={true} />);

    await waitFor(() => {
      expect(screen.getByText('v2')).toBeDefined();
      expect(screen.getByText('v1')).toBeDefined();
    });

    expect(screen.getByText('当前生效')).toBeDefined();
    expect(screen.getByText('修复表单校验BUG')).toBeDefined();
    expect(screen.getByText('初始版本')).toBeDefined();
    expect(screen.getByText('回滚至此版本')).toBeDefined();
  });

  it('handles rollback to previous version with reason prompt', async () => {
    vi.mocked(versionApi.list).mockResolvedValue([
      {
        versionId: 'ver-2',
        versionNo: 2,
        changeLog: 'v2',
        status: 'PUBLISHED',
        sourceType: 'ZIP',
        sourceSize: 2048,
        fileCount: 5,
        expandedSize: 8192,
        isCurrent: true,
        createdAt: '2026-09-01T12:00:00Z',
      },
      {
        versionId: 'ver-1',
        versionNo: 1,
        changeLog: 'v1',
        status: 'PUBLISHED',
        sourceType: 'ZIP',
        sourceSize: 1024,
        fileCount: 3,
        expandedSize: 4096,
        isCurrent: false,
        createdAt: '2026-09-01T10:00:00Z',
      },
    ]);
    vi.mocked(versionApi.switchCurrent).mockResolvedValue({} as any);

    renderList(<VersionList prototypeId="proto-123" canManage={true} />);

    await waitFor(() => {
      expect(screen.getByText('回滚至此版本')).toBeDefined();
    });

    fireEvent.click(screen.getByText('回滚至此版本'));

    expect(screen.getByText('确认切换生效版本至 v1')).toBeDefined();
    const reasonInput = screen.getByPlaceholderText('请填写版本回滚或切换原因...');
    fireEvent.change(reasonInput, { target: { value: '线上紧急降级' } });

    fireEvent.click(screen.getByText('确认切换'));

    await waitFor(() => {
      expect(versionApi.switchCurrent).toHaveBeenCalledWith('proto-123', 'ver-1', {
        expectedCurrentVersionNo: 2,
        reason: '线上紧急降级',
      });
    });
  });

  it('triggers download ticket when clicking download', async () => {
    vi.mocked(versionApi.list).mockResolvedValue([
      {
        versionId: 'ver-1',
        versionNo: 1,
        changeLog: 'v1',
        status: 'PUBLISHED',
        sourceType: 'ZIP',
        sourceSize: 1024,
        fileCount: 3,
        expandedSize: 4096,
        isCurrent: true,
        createdAt: '2026-09-01T10:00:00Z',
      },
    ]);
    vi.mocked(versionApi.createDownloadTicket).mockResolvedValue({
      url: '/api/v1/downloads/ticket-123',
      expiresAt: '2026-09-01T12:05:00Z',
    });
    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);

    renderList(<VersionList prototypeId="proto-123" canManage={true} />);

    await waitFor(() => {
      expect(screen.getByText('下载源文件')).toBeDefined();
    });

    fireEvent.click(screen.getByText('下载源文件'));

    await waitFor(() => {
      expect(versionApi.createDownloadTicket).toHaveBeenCalledWith('proto-123', 'ver-1');
      expect(openSpy).toHaveBeenCalledWith('/api/v1/downloads/ticket-123', '_blank');
    });

    openSpy.mockRestore();
  });

  it('shows download without manage actions when the user can only download', async () => {
    vi.mocked(versionApi.list).mockResolvedValue([
      {
        versionId: 'ver-1',
        versionNo: 1,
        changeLog: 'v1',
        status: 'PUBLISHED',
        sourceType: 'HTML',
        sourceSize: 128,
        fileCount: 1,
        expandedSize: 128,
        isCurrent: false,
        createdAt: '2026-09-01T10:00:00Z',
      },
    ]);

    renderList(<VersionList prototypeId="proto-123" canManage={false} canDownload />);

    expect(await screen.findByText('下载源文件')).toBeDefined();
    expect(screen.queryByText('发布新版本')).toBeNull();
    expect(screen.queryByText('回滚至此版本')).toBeNull();
  });
});
