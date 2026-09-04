import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { PrototypeSpecEditor } from './PrototypeSpecEditor';
import { specApi } from './api';

vi.mock('./api', () => ({
  specApi: {
    get: vi.fn(),
    update: vi.fn(),
  },
}));

describe('PrototypeSpecEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads and renders structured spec sections and switches to preview', async () => {
    vi.mocked(specApi.get).mockResolvedValue({
      prototypePublicId: 'proto-123',
      goal: '打造企业级原型平台',
      coreFlow: '登录 -> 上传 -> 校验 -> 预览',
      interactionRules: '弹窗二次确认',
      businessConstraints: '限内部网络',
      dataRequirements: 'UTF-8 字符集',
      acceptanceNotes: '用例全部绿灯',
      markdownExtra: '### 补充备忘\n- 项1\n- 项2',
      rowVersion: 1,
      updatedAt: '2026-09-01T12:00:00Z',
    });

    render(<PrototypeSpecEditor prototypeId="proto-123" />);

    expect(screen.getByText('加载说明中...')).toBeDefined();

    await waitFor(() => {
      expect(screen.getByDisplayValue('打造企业级原型平台')).toBeDefined();
      expect(screen.getByDisplayValue('登录 -> 上传 -> 校验 -> 预览')).toBeDefined();
    });

    // Switch to preview mode
    fireEvent.click(screen.getByText('预览模式'));

    await waitFor(() => {
      expect(screen.getByText('打造企业级原型平台')).toBeDefined();
      expect(screen.getByText('补充备忘')).toBeDefined();
    });
  });

  it('shows conflict banner when optimistic locking conflict occurs', async () => {
    vi.mocked(specApi.get).mockResolvedValue({
      prototypePublicId: 'proto-123',
      goal: '旧目标',
      coreFlow: '',
      interactionRules: '',
      businessConstraints: '',
      dataRequirements: '',
      acceptanceNotes: '',
      markdownExtra: '',
      rowVersion: 1,
      updatedAt: '2026-09-01T12:00:00Z',
    });

    vi.mocked(specApi.update).mockRejectedValue({
      code: 'RESOURCE_VERSION_CONFLICT',
      message: '说明已被他人修改',
    });

    render(<PrototypeSpecEditor prototypeId="proto-123" />);

    await waitFor(() => {
      expect(screen.getByDisplayValue('旧目标')).toBeDefined();
    });

    const goalInput = screen.getByDisplayValue('旧目标');
    fireEvent.change(goalInput, { target: { value: '我的新目标' } });

    // Wait for 800ms debounce
    await waitFor(
      () => {
        expect(screen.getByText('⚠️ 检测到版本冲突')).toBeDefined();
        expect(screen.getByText('复制本地草稿到剪贴板')).toBeDefined();
      },
      { timeout: 2000 }
    );
  });
});
