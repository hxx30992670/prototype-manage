import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { PrototypePreview } from './PrototypePreview';
import { versionApi } from './api';

vi.mock('./api', () => ({
  versionApi: {
    createPreviewTicket: vi.fn(),
  },
}));

describe('PrototypePreview', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders iframe with strict sandbox attributes and absolute preview URL', async () => {
    const mockTicket = {
      ticket: 'tick-abc',
      contentUrl: 'http://preview.corp.test/content/c/tick-abc/index.html',
      expiresAt: '2026-09-01T14:00:00Z',
    };

    vi.mocked(versionApi.createPreviewTicket).mockResolvedValue(mockTicket);

    render(<PrototypePreview prototypeId="proto-123" title="测试预览" />);

    expect(screen.getByText('正在生成安全预览沙箱会话...')).toBeDefined();

    await waitFor(() => {
      const iframe = screen.getByTitle('测试预览') as HTMLIFrameElement;
      expect(iframe).toBeDefined();
      expect(iframe.src).toBe('http://preview.corp.test/content/c/tick-abc/index.html');
      expect(iframe.getAttribute('sandbox')).toBe('allow-scripts allow-forms allow-modals allow-downloads');
      expect(iframe.getAttribute('referrerPolicy')).toBe('no-referrer');
    });

    expect(screen.getByText('沙箱隔离模式')).toBeDefined();
  });

  it('allows switching device viewports', async () => {
    const mockTicket = {
      ticket: 'tick-abc',
      contentUrl: 'http://preview.corp.test/content/c/tick-abc/index.html',
      expiresAt: '2026-09-01T14:00:00Z',
    };

    vi.mocked(versionApi.createPreviewTicket).mockResolvedValue(mockTicket);

    render(<PrototypePreview prototypeId="proto-123" title="测试预览" />);

    await waitFor(() => {
      expect(screen.getByText('手机')).toBeDefined();
    });

    fireEvent.click(screen.getByText('手机'));
    const iframe = screen.getByTitle('测试预览');
    const container = iframe.parentElement;
    expect(container?.style.width).toBe('375px');

    fireEvent.click(screen.getByText('平板'));
    expect(container?.style.width).toBe('768px');

    fireEvent.click(screen.getByText('桌面'));
    expect(container?.style.width).toBe('1280px');
  });

  it('shows error state when ticket request fails', async () => {
    vi.mocked(versionApi.createPreviewTicket).mockRejectedValue({
      code: 'ACCESS_DENIED',
      message: '无权访问该受限原型',
    });

    render(<PrototypePreview prototypeId="proto-123" title="测试预览" />);

    await waitFor(() => {
      expect(screen.getByText('无法加载预览')).toBeDefined();
      expect(screen.getByText('无权访问该受限原型')).toBeDefined();
    });
  });

  it('toggles fullscreen mode and handles exit cleanly', async () => {
    const mockTicket = {
      ticket: 'tick-abc',
      contentUrl: 'http://preview.corp.test/content/c/tick-abc/index.html',
      expiresAt: '2026-09-01T14:00:00Z',
    };

    vi.mocked(versionApi.createPreviewTicket).mockResolvedValue(mockTicket);

    render(<PrototypePreview prototypeId="proto-123" title="测试预览" />);

    await waitFor(() => {
      expect(screen.getByText('全屏预览')).toBeDefined();
    });

    const toggleBtn = screen.getByText('全屏预览');
    fireEvent.click(toggleBtn);

    expect(screen.getByText('退出全屏')).toBeDefined();

    // Click again to exit
    fireEvent.click(screen.getByText('退出全屏'));
    expect(screen.getByText('全屏预览')).toBeDefined();
  });
});

