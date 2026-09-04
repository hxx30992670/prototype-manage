import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ShareSettings } from './ShareSettings';
import { shareAdminApi } from './api';

vi.mock('./api', () => ({
  shareAdminApi: {
    list: vi.fn(),
    create: vi.fn(),
    rotateToken: vi.fn(),
    resetPassword: vi.fn(),
    updateStatus: vi.fn(),
  },
}));

describe('ShareSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders creation modal with readable input styles and high contrast', async () => {
    vi.mocked(shareAdminApi.list).mockResolvedValue([]);

    render(<ShareSettings prototypeId="proto-123" canManage={true} />);

    await waitFor(() => {
      expect(screen.getByText('暂无分享链接，点击右上角创建新的对外分享链接')).toBeDefined();
    });

    const createBtn = screen.getByText('+ 创建分享链接');
    fireEvent.click(createBtn);

    expect(screen.getByRole('heading', { name: '创建对外分享链接' })).toBeDefined();

    const nameInput = screen.getByPlaceholderText('例如：市场部评审、客户演示...');
    expect(nameInput).toBeDefined();
    expect(nameInput.className).toContain('text-gray-900');
    expect(nameInput.className).toContain('bg-white');

    const pwdInput = screen.getByPlaceholderText('留空则无需密码即可访问');
    expect(pwdInput).toBeDefined();
    expect(pwdInput.className).toContain('text-gray-900');
    expect(pwdInput.className).toContain('bg-white');

    const scopeSelect = screen.getByDisplayValue('不公开 (仅预览原型页面)');
    expect(scopeSelect).toBeDefined();
    expect(scopeSelect.className).toContain('text-gray-900');
    expect(scopeSelect.className).toContain('bg-white');
  });

  it('submits share creation form with entered values', async () => {
    vi.mocked(shareAdminApi.list).mockResolvedValue([]);
    vi.mocked(shareAdminApi.create).mockResolvedValue({
      shareId: 'share-1',
      rawUrl: 'http://preview.corp.test/s/secret-123',
      secretAvailable: true,
    });

    render(<ShareSettings prototypeId="proto-123" canManage={true} />);

    await waitFor(() => {
      expect(screen.getByText('+ 创建分享链接')).toBeDefined();
    });

    fireEvent.click(screen.getByText('+ 创建分享链接'));

    const nameInput = screen.getByPlaceholderText('例如：市场部评审、客户演示...');
    fireEvent.change(nameInput, { target: { value: '市场部评审' } });

    const submitBtn = screen.getByText('生成链接');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(shareAdminApi.create).toHaveBeenCalledWith(
        'proto-123',
        expect.objectContaining({
          name: '市场部评审',
        }),
        expect.any(String)
      );
    });
  });
});
