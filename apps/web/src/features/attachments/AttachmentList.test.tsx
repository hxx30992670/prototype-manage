import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import { AttachmentList } from './AttachmentList';
import { attachmentApi, AttachmentItem } from './api';

vi.mock('./api', () => ({
  attachmentApi: {
    getDownloadTicket: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('AttachmentList', () => {
  const mockAttachments: AttachmentItem[] = [
    {
      publicId: 'att-1',
      prototypePublicId: 'proto-1',
      name: '架构图.png',
      type: 'IMAGE',
      purpose: '技术架构概览',
      accessScope: 'PUBLIC',
      size: 1048576, // 1 MB
      mimeType: 'image/png',
      createdBy: '张三',
      createdAt: '2026-09-01T10:00:00Z',
    },
    {
      publicId: 'att-2',
      prototypePublicId: 'proto-1',
      name: '内部设计底稿.pdf',
      type: 'DOCUMENT',
      purpose: '业务细节与保密字段',
      accessScope: 'INTERNAL',
      size: 2048,
      mimeType: 'application/pdf',
      createdBy: '李四',
      createdAt: '2026-09-01T11:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders attachment list with correct badges and metadata', () => {
    render(<AttachmentList attachments={mockAttachments} canManage={true} />);

    expect(screen.getByText('架构图.png')).toBeDefined();
    expect(screen.getByText('公开分享')).toBeDefined();
    expect(screen.getByText('1.0 MB')).toBeDefined();

    expect(screen.getByText('内部设计底稿.pdf')).toBeDefined();
    expect(screen.getByText('内部可见')).toBeDefined();
  });

  it('triggers download ticket API on download click', async () => {
    vi.mocked(attachmentApi.getDownloadTicket).mockResolvedValue({
      url: '/api/v1/downloads/ticket-123',
      expiresAt: '2026-09-01T12:00:00Z',
    });

    render(<AttachmentList attachments={mockAttachments} canManage={false} />);

    const downloadButtons = screen.getAllByText('下载');
    fireEvent.click(downloadButtons[0]);

    await waitFor(() => {
      expect(attachmentApi.getDownloadTicket).toHaveBeenCalledWith('att-1');
    });
  });

  it('confirms and deletes attachment when manager clicks delete', async () => {
    const confirmSpy = vi.spyOn(Modal, 'confirm').mockImplementation((config: any) => {
      config.onOk?.();
      return { destroy: () => {}, update: () => {} };
    });
    vi.mocked(attachmentApi.delete).mockResolvedValue();
    const onRefresh = vi.fn();

    render(<AttachmentList attachments={mockAttachments} canManage={true} onRefresh={onRefresh} />);

    const deleteButtons = screen.getAllByText('删除');
    fireEvent.click(deleteButtons[0]);

    expect(confirmSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '确定要删除附件吗？',
      })
    );

    await waitFor(() => {
      expect(attachmentApi.delete).toHaveBeenCalledWith('att-1');
      expect(onRefresh).toHaveBeenCalled();
    });
  });
});
