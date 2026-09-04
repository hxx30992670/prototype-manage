import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ShareSecretDialog } from './ShareSecretDialog';

describe('ShareSecretDialog', () => {
  it('renders raw url, warning message and copies url on click', async () => {
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextMock,
      },
    });

    const onClose = vi.fn();
    render(
      <ShareSecretDialog
        open={true}
        rawUrl="http://preview.corp.test/s/secret-token-123"
        onClose={onClose}
      />
    );

    expect(screen.getByText(/仅在此处展示一次/)).toBeDefined();
    expect(screen.getByDisplayValue('http://preview.corp.test/s/secret-token-123')).toBeDefined();

    const copyBtn = screen.getByText('复制链接');
    fireEvent.click(copyBtn);

    expect(writeTextMock).toHaveBeenCalledWith('http://preview.corp.test/s/secret-token-123');

    const closeBtn = screen.getByText('我已复制，关闭窗口');
    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalled();
  });
});
