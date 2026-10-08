import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { CommentThread } from './CommentThread';
import { commentApi, publicCommentApi, type CommentItem } from './api';

vi.mock('./api', () => ({
  commentApi: {
    list: vi.fn(),
    create: vi.fn(),
    resolve: vi.fn(),
    delete: vi.fn(),
  },
  publicCommentApi: {
    list: vi.fn(),
    createGuest: vi.fn(),
  },
}));

describe('CommentThread', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders comment list and allows filtering', async () => {
    (commentApi.list as any).mockResolvedValue([
      {
        publicId: 'c1',
        prototypePublicId: 'p1',
        versionPublicId: 'v1',
        versionNo: 1,
        content: '按钮点击无动画',
        status: 'OPEN',
        authorType: 'USER',
        authorName: '张三',
        rowVersion: 0,
        createdAt: new Date().toISOString(),
        isDeleted: false,
        replies: [],
      },
    ]);

    render(
      <CommentThread
        prototypeId="p1"
        currentVersionId="v1"
        currentVersionNo={1}
        canManage={true}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('按钮点击无动画')).toBeDefined();
      expect(screen.getByText('张三')).toBeDefined();
      expect(screen.getByText('待处理')).toBeDefined();
    });
  });

  it.each([false, true])('renders Markdown and safe font colors in comments, replies and resolve notes (public: %s)', async (isPublic) => {
    const comments: CommentItem[] = [{
      publicId: 'markdown-comment',
      prototypePublicId: 'p1',
      versionPublicId: 'v1',
      versionNo: 1,
      content: '### 评论标题\n\n**评论重点**\n\n- 第一项\n- 第二项\n\n<span style="color:red;position:fixed" onclick="alert(1)">红色说明</span>',
      status: 'RESOLVED',
      authorType: 'USER',
      authorName: '张三',
      resolvedBy: '李四',
      resolveNote: '**解决重点** <font color="#00aa00">绿色说明</font>',
      rowVersion: 0,
      createdAt: '2026-10-08T00:00:00Z',
      isDeleted: false,
      replies: [{
        publicId: 'markdown-reply',
        content: '**回复重点** <span style="color:blue">蓝色说明</span>',
        authorType: 'USER',
        authorName: '李四',
        createdAt: '2026-10-08T01:00:00Z',
        isDeleted: false,
      }],
    }];
    vi.mocked(commentApi.list).mockResolvedValue(comments);
    vi.mocked(publicCommentApi.list).mockResolvedValue(comments);

    const { container } = render(
      <CommentThread prototypeId="p1" currentVersionId="v1" isPublic={isPublic} publicToken="share-token" />,
    );

    expect(await screen.findByRole('heading', { name: '评论标题', level: 3 })).toBeInTheDocument();
    for (const text of ['评论重点', '回复重点', '解决重点']) {
      expect(screen.getByText(text).tagName).toBe('STRONG');
    }
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual(['第一项', '第二项']);
    expect(screen.getByText('红色说明')).toHaveStyle({ color: 'rgb(255, 0, 0)' });
    expect(screen.getByText('绿色说明')).toHaveStyle({ color: 'rgb(0, 170, 0)' });
    expect(screen.getByText('蓝色说明')).toHaveStyle({ color: 'rgb(0, 0, 255)' });
    expect(container.querySelector('[onclick]')).toBeNull();
    expect(screen.getByText('红色说明').style.position).toBe('');
  });

  it('creates new comment when submitting form', async () => {
    (commentApi.list as any).mockResolvedValue([]);
    (commentApi.create as any).mockResolvedValue({
      publicId: 'c2',
      content: '新评论内容',
    });

    render(
      <CommentThread
        prototypeId="p1"
        currentVersionId="v1"
        currentVersionNo={1}
        canManage={true}
      />
    );

    const textarea = screen.getByPlaceholderText(/针对当前版本提出评审意见/);
    fireEvent.change(textarea, { target: { value: '测试评论输入' } });

    const submitBtn = screen.getByText('发表评论');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(commentApi.create).toHaveBeenCalledWith(
        'p1',
        expect.objectContaining({
          versionPublicId: 'v1',
          content: '测试评论输入',
        }),
        expect.stringMatching(/^comment-/)
      );
    });
  });

  it('guest comment on share page sends the current version id', async () => {
    vi.mocked(publicCommentApi.list).mockResolvedValue([]);
    vi.mocked(publicCommentApi.createGuest).mockResolvedValue({} as CommentItem);

    render(
      <CommentThread
        currentVersionId="ver-real-id"
        currentVersionNo={2}
        isPublic
        publicToken="share-token"
        csrfToken="csrf-token"
      />
    );

    const textarea = screen.getByPlaceholderText(/针对当前版本提出评审意见/);
    fireEvent.change(textarea, { target: { value: '访客评论' } });
    fireEvent.click(screen.getByText('发表评论'));

    await waitFor(() => {
      expect(publicCommentApi.createGuest).toHaveBeenCalledWith(
        'share-token',
        expect.objectContaining({
          versionPublicId: 'ver-real-id',
          content: '访客评论',
        }),
        'csrf-token',
        expect.stringMatching(/^comment-/)
      );
    });
  });
});
