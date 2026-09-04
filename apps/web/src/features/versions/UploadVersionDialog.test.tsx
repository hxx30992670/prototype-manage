import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { UploadVersionDialog } from './UploadVersionDialog';

vi.mock('./api', () => ({
  versionApi: {
    createUpload: vi.fn(),
    directUpload: vi.fn(),
    completeUpload: vi.fn(),
    createVersion: vi.fn(),
  },
}));

describe('UploadVersionDialog', () => {
  const defaultProps = {
    prototypeId: 'proto-123',
    isOpen: true,
    onClose: vi.fn(),
    onSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correctly with readable changeLog textarea styling', () => {
    render(<UploadVersionDialog {...defaultProps} />);

    expect(screen.getByText('上传发布新版本')).toBeDefined();
    expect(screen.getByText(/变更说明/)).toBeDefined();

    const textarea = screen.getByPlaceholderText('请详细描述本次版本改动内容...');
    expect(textarea).toBeDefined();

    // Verify textarea has proper contrast styling (dark text color and white background)
    expect(textarea.className).toContain('text-gray-900');
    expect(textarea.className).toContain('bg-white');
  });

  it('allows user to type into changeLog textarea', () => {
    render(<UploadVersionDialog {...defaultProps} />);

    const textarea = screen.getByPlaceholderText('请详细描述本次版本改动内容...') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '修复若干界面问题' } });

    expect(textarea.value).toBe('修复若干界面问题');
  });

  it('calls onClose when close button is clicked', () => {
    render(<UploadVersionDialog {...defaultProps} />);

    const closeBtn = screen.getByText('✕');
    fireEvent.click(closeBtn);

    expect(defaultProps.onClose).toHaveBeenCalledTimes(1);
  });
});
