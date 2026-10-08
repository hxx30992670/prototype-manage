import React, { useState, useEffect } from 'react';
import { Modal, Input, message } from 'antd';
import { CommentItem, commentApi, publicCommentApi } from './api';
import { SafeMarkdown } from '@/lib/markdown';

interface CommentThreadProps {
  prototypeId?: string;
  currentVersionId: string;
  currentVersionNo?: number;
  canManage?: boolean;
  isPublic?: boolean;
  publicToken?: string;
  csrfToken?: string;
}

export const CommentThread: React.FC<CommentThreadProps> = ({
  prototypeId,
  currentVersionId,
  currentVersionNo,
  canManage = false,
  isPublic = false,
  publicToken,
  csrfToken,
}) => {
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [filterVersion, setFilterVersion] = useState<'CURRENT' | 'ALL'>('ALL');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'OPEN' | 'RESOLVED'>('ALL');

  // Input states
  const [newContent, setNewContent] = useState('');
  const [replyingToId, setReplyingToId] = useState<string | null>(null);
  const [replyContent, setReplyContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Antd Modal for resolving comment
  const [resolveTarget, setResolveTarget] = useState<CommentItem | null>(null);
  const [resolveNote, setResolveNote] = useState('');
  const [resolving, setResolving] = useState(false);

  const fetchComments = async () => {
    try {
      setLoading(true);
      setError(null);
      if (isPublic && publicToken) {
        const list = await publicCommentApi.list(publicToken);
        setComments(list);
      } else if (prototypeId) {
        const versionParam = filterVersion === 'CURRENT' ? currentVersionId : undefined;
        const statusParam = filterStatus !== 'ALL' ? filterStatus : undefined;
        const list = await commentApi.list(prototypeId, versionParam, statusParam);
        setComments(list);
      }
    } catch (e: any) {
      setError(e?.message || '加载评论失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchComments();
  }, [prototypeId, currentVersionId, filterVersion, filterStatus, isPublic, publicToken]);

  const handleCreateComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newContent.trim()) return;

    try {
      setSubmitting(true);
      const idempotencyKey = 'comment-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
      const payload = {
        versionPublicId: currentVersionId,
        content: newContent,
      };

      if (isPublic && publicToken) {
        await publicCommentApi.createGuest(publicToken, payload, csrfToken, idempotencyKey);
      } else if (prototypeId) {
        await commentApi.create(prototypeId, payload, idempotencyKey);
      }

      setNewContent('');
      message.success('评论发布成功');
      fetchComments();
    } catch (err: any) {
      message.error('发布评论失败: ' + (err?.response?.data?.message || err?.message || '未知错误'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateReply = async (parentCommentId: string) => {
    if (!replyContent.trim()) return;

    try {
      setSubmitting(true);
      const idempotencyKey = 'reply-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8);
      const payload = {
        versionPublicId: currentVersionId,
        parentCommentPublicId: parentCommentId,
        content: replyContent,
      };

      if (isPublic && publicToken) {
        await publicCommentApi.createGuest(publicToken, payload, csrfToken, idempotencyKey);
      } else if (prototypeId) {
        await commentApi.create(prototypeId, payload, idempotencyKey);
      }

      setReplyContent('');
      setReplyingToId(null);
      message.success('回复已发送');
      fetchComments();
    } catch (err: any) {
      message.error('回复失败: ' + (err?.response?.data?.message || err?.message || '未知错误'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleResolve = (comment: CommentItem) => {
    if (!prototypeId) return;
    if (comment.status === 'RESOLVED') {
      Modal.confirm({
        title: '确定要重新打开此评论吗？',
        content: '该评论的状态将从“已解决”重置为“待处理”。',
        okText: '确定重开',
        cancelText: '取消',
        onOk: async () => {
          try {
            await commentApi.resolve(prototypeId, comment.publicId, {
              status: 'OPEN',
              rowVersion: comment.rowVersion,
            });
            message.success('评论已重新打开');
            fetchComments();
          } catch (err: any) {
            message.error('操作失败: ' + (err?.response?.data?.message || err?.message || '未知错误'));
          }
        },
      });
    } else {
      setResolveTarget(comment);
      setResolveNote('');
    }
  };

  const handleConfirmResolve = async () => {
    if (!prototypeId || !resolveTarget) return;
    try {
      setResolving(true);
      await commentApi.resolve(prototypeId, resolveTarget.publicId, {
        status: 'RESOLVED',
        resolveNote: resolveNote.trim() || undefined,
        rowVersion: resolveTarget.rowVersion,
      });
      message.success('评论已标记为已解决');
      setResolveTarget(null);
      setResolveNote('');
      fetchComments();
    } catch (err: any) {
      message.error('操作失败: ' + (err?.response?.data?.message || err?.message || '未知错误'));
    } finally {
      setResolving(false);
    }
  };

  const handleDelete = (commentId: string) => {
    if (!prototypeId) return;
    Modal.confirm({
      title: '确定要删除此评论吗？',
      content: '删除后评论及所有回复将无法恢复。',
      okText: '确定删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await commentApi.delete(prototypeId, commentId);
          message.success('评论已删除');
          fetchComments();
        } catch (err: any) {
          message.error('删除失败: ' + (err?.message || '未知错误'));
        }
      },
    });
  };

  const filteredComments = comments.filter((c) => {
    if (filterVersion === 'CURRENT' && c.versionPublicId !== currentVersionId) return false;
    if (filterStatus !== 'ALL' && c.status !== filterStatus) return false;
    return true;
  });

  return (
    <div className="rounded-xl border bg-white p-6 shadow-sm space-y-6 text-gray-900 [color-scheme:light]">
      {/* Title & Filters */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b pb-4">
        <div>
          <h3 className="text-base font-bold text-gray-900">版本评审与协作讨论</h3>
          <p className="text-xs text-gray-500">
            支持针对版本提出评审意见，支持两级回复与闭环处理
          </p>
        </div>

        {!isPublic && (
          <div className="flex items-center gap-3 text-xs">
            <select
              value={filterVersion}
              onChange={(e) => setFilterVersion(e.target.value as any)}
              className="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs text-gray-900 shadow-sm focus:outline-none"
            >
              <option value="ALL">全部版本评论</option>
              <option value="CURRENT">仅当前版本 ({currentVersionNo ? `v${currentVersionNo}` : '最新'})</option>
            </select>

            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value as any)}
              className="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs text-gray-900 shadow-sm focus:outline-none"
            >
              <option value="ALL">全部状态</option>
              <option value="OPEN">仅待处理</option>
              <option value="RESOLVED">已解决</option>
            </select>
          </div>
        )}
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-xs text-red-700">
          {error}
        </div>
      )}

      {/* Input Box */}
      <form onSubmit={handleCreateComment} className="space-y-3">
        <textarea
          rows={3}
          required
          value={newContent}
          onChange={(e) => setNewContent(e.target.value)}
          placeholder={`针对当前版本提出评审意见或交互反馈...`}
          className="w-full rounded-lg border border-gray-300 bg-white p-3 text-xs text-gray-900 placeholder:text-gray-400 shadow-sm focus:border-blue-500 focus:outline-none"
        />
        <div className="flex justify-end">
          <button
            type="submit"
            disabled={submitting}
            className="rounded-md bg-blue-600 px-4 py-2 text-xs font-semibold text-white shadow hover:bg-blue-700 disabled:opacity-50"
          >
            {submitting ? '发布中...' : '发表评论'}
          </button>
        </div>
      </form>

      {/* Comments List */}
      <div className="space-y-4 pt-2">
        {loading ? (
          <div className="py-6 text-center text-xs text-gray-500">加载评论中...</div>
        ) : filteredComments.length === 0 ? (
          <div className="py-8 text-center text-xs text-gray-400">
            暂无相关评审评论，快来抢先发言吧！
          </div>
        ) : (
          filteredComments.map((c) => (
            <div
              key={c.publicId}
              className={`rounded-lg border p-4 transition-colors ${
                c.status === 'RESOLVED' ? 'bg-gray-50 border-gray-200' : 'bg-white border-gray-200'
              }`}
            >
              {/* Comment Header */}
              <div className="flex items-center justify-between text-xs">
                <div className="flex items-center gap-2">
                  <span className="font-semibold text-gray-900">{c.authorName}</span>
                  <span
                    className={`rounded px-1.5 py-0.5 text-[10px] ${
                      c.authorType === 'GUEST'
                        ? 'bg-amber-100 text-amber-800'
                        : 'bg-blue-100 text-blue-800'
                    }`}
                  >
                    {c.authorType === 'GUEST' ? '访客' : '成员'}
                  </span>
                  <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] text-gray-600 font-mono">
                    v{c.versionNo}
                  </span>
                  <span className="text-gray-400">
                    {new Date(c.createdAt).toLocaleString()}
                  </span>
                </div>

                <div className="flex items-center gap-2">
                  {c.status && (
                    <span
                      className={`rounded px-2 py-0.5 text-[10px] font-semibold ${
                        c.status === 'RESOLVED'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-amber-100 text-amber-800'
                      }`}
                    >
                      {c.status === 'RESOLVED' ? '已解决' : '待处理'}
                    </span>
                  )}
                  {canManage && !isPublic && (
                    <button
                      onClick={() => handleResolve(c)}
                      className="text-blue-600 hover:text-blue-800 font-medium text-[11px]"
                    >
                      {c.status === 'RESOLVED' ? '重新打开' : '标记解决'}
                    </button>
                  )}
                  {!isPublic && (
                    <button
                      onClick={() => handleDelete(c.publicId)}
                      className="text-red-500 hover:text-red-700 text-[11px]"
                    >
                      删除
                    </button>
                  )}
                </div>
              </div>

              {/* Comment Body */}
              <SafeMarkdown
                source={c.content}
                className={`mt-2 text-xs ${c.isDeleted ? 'italic text-gray-400' : 'text-gray-800'}`}
              />

              {/* Resolve Note if any */}
              {c.resolveNote && (
                <div className="mt-2 rounded bg-emerald-50 p-2 text-xs text-emerald-900">
                  <span className="font-semibold">解决说明 ({c.resolvedBy})：</span>
                  <SafeMarkdown source={c.resolveNote} className="text-xs text-emerald-900" />
                </div>
              )}

              {/* Replies */}
              {c.replies && c.replies.length > 0 && (
                <div className="mt-3 ml-4 space-y-2 border-l-2 border-gray-200 pl-3">
                  {c.replies.map((r) => (
                    <div key={r.publicId} className="rounded bg-gray-50 p-2 text-xs">
                      <div className="flex items-center gap-2 text-[11px]">
                        <span className="font-semibold text-gray-900">{r.authorName}</span>
                        <span className="text-gray-400">{new Date(r.createdAt).toLocaleString()}</span>
                      </div>
                      <SafeMarkdown
                        source={r.content}
                        className={`mt-1 text-xs ${r.isDeleted ? 'italic text-gray-400' : 'text-gray-800'}`}
                      />
                    </div>
                  ))}
                </div>
              )}

              {/* Reply Button / Box */}
              <div className="mt-3">
                {replyingToId === c.publicId ? (
                  <div className="mt-2 ml-4 flex gap-2">
                    <input
                      type="text"
                      value={replyContent}
                      onChange={(e) => setReplyContent(e.target.value)}
                      placeholder={`回复 ${c.authorName}...`}
                      className="flex-1 rounded-md border border-gray-300 bg-white px-3 py-1 text-xs text-gray-900 placeholder:text-gray-400 focus:border-blue-500 focus:outline-none"
                    />
                    <button
                      onClick={() => handleCreateReply(c.publicId)}
                      disabled={submitting}
                      className="rounded-md bg-blue-600 px-3 py-1 text-xs text-white hover:bg-blue-700 disabled:opacity-50"
                    >
                      回复
                    </button>
                    <button
                      onClick={() => {
                        setReplyingToId(null);
                        setReplyContent('');
                      }}
                      className="rounded-md border border-gray-300 bg-white px-3 py-1 text-xs text-gray-700 hover:bg-gray-50"
                    >
                      取消
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => setReplyingToId(c.publicId)}
                    className="text-xs font-medium text-gray-500 hover:text-blue-600"
                  >
                    回复
                  </button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <Modal
        title="标记评论为已解决"
        open={Boolean(resolveTarget)}
        onOk={handleConfirmResolve}
        onCancel={() => setResolveTarget(null)}
        confirmLoading={resolving}
        okText="确定解决"
        cancelText="取消"
        destroyOnHidden
      >
        <div className="space-y-3 py-2">
          <p className="text-xs text-mute">请输入处理备注（例如：已在v2优化修改）：</p>
          <Input.TextArea
            rows={3}
            value={resolveNote}
            onChange={(e) => setResolveNote(e.target.value)}
            placeholder="请输入处理备注（例如：已在v2优化修改）"
            maxLength={300}
            showCount
          />
        </div>
      </Modal>
    </div>
  );
};
