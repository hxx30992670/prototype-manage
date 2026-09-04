import React, { useState, useEffect } from 'react';
import { Modal, Input, message } from 'antd';
import { ShareLinkItem, shareAdminApi, CreateSharePayload } from './api';
import { ShareSecretDialog } from './ShareSecretDialog';

interface ShareSettingsProps {
  prototypeId: string;
  canManage?: boolean;
}

export const ShareSettings: React.FC<ShareSettingsProps> = ({
  prototypeId,
  canManage = false,
}) => {
  const [shares, setShares] = useState<ShareLinkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Password reset modal state
  const [resetTarget, setResetTarget] = useState<ShareLinkItem | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [resetting, setResetting] = useState(false);

  // Creation modal state
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateSharePayload>({
    name: '',
    password: '',
    specScope: 'NONE',
    allowComment: false,
    allowPublicAttachment: false,
  });
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Secret dialog state
  const [secretDialogUrl, setSecretDialogUrl] = useState<string | null>(null);

  const fetchShares = async () => {
    try {
      setLoading(true);
      setError(null);
      const list = await shareAdminApi.list(prototypeId);
      setShares(list);
    } catch (e: any) {
      setError(e?.message || '获取分享列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchShares();
  }, [prototypeId]);

  const handleOpenCreate = () => {
    setCreateForm({
      name: '',
      password: '',
      specScope: 'NONE',
      allowComment: false,
      allowPublicAttachment: false,
    });
    setIdempotencyKey('idemp-' + Date.now() + '-' + Math.random().toString(36).substring(2, 8));
    setCreateModalOpen(true);
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createForm.name.trim()) return;

    try {
      setSubmitting(true);
      const res = await shareAdminApi.create(prototypeId, createForm, idempotencyKey);
      setCreateModalOpen(false);
      message.success('创建对外分享链接成功');
      fetchShares();
      if (res.rawUrl) {
        setSecretDialogUrl(res.rawUrl);
      }
    } catch (err: any) {
      message.error('创建分享链接失败: ' + (err?.message || '未知错误'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRotate = (share: ShareLinkItem) => {
    Modal.confirm({
      title: '轮换分享链接 Token',
      content: `确定要轮换分享链接 "${share.name}" 的访问 Token 吗？轮换后旧链接与所有已有免登会话均将立即失效！`,
      okText: '确定轮换',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await shareAdminApi.rotateToken(prototypeId, share.shareId);
          message.success('Token 轮换成功');
          fetchShares();
          if (res.rawUrl) {
            setSecretDialogUrl(res.rawUrl);
          }
        } catch (err: any) {
          message.error('轮换 Token 失败: ' + (err?.message || '未知错误'));
        }
      },
    });
  };

  const handleToggleStatus = (share: ShareLinkItem) => {
    const nextStatus = share.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    const actionLabel = nextStatus === 'DISABLED' ? '停用' : '启用';
    Modal.confirm({
      title: `确定要${actionLabel}该分享链接吗？`,
      content: `分享链接 "${share.name}" 将被${actionLabel}。${nextStatus === 'DISABLED' ? '停用后通过该链接将无法继续访问原型。' : '启用后该链接可正常访问。'}`,
      okText: `确定${actionLabel}`,
      okType: nextStatus === 'DISABLED' ? 'danger' : 'primary',
      cancelText: '取消',
      onOk: async () => {
        try {
          await shareAdminApi.updateStatus(prototypeId, share.shareId, nextStatus);
          message.success(`已${actionLabel}分享链接`);
          fetchShares();
        } catch (err: any) {
          message.error(`${actionLabel}失败: ` + (err?.message || '未知错误'));
        }
      },
    });
  };

  const handleResetPassword = (share: ShareLinkItem) => {
    setResetTarget(share);
    setNewPassword('');
  };

  const handleConfirmResetPassword = async () => {
    if (!resetTarget) return;
    try {
      setResetting(true);
      await shareAdminApi.resetPassword(prototypeId, resetTarget.shareId, newPassword);
      message.success('密码重置成功，已失效旧的密码会话');
      setResetTarget(null);
      setNewPassword('');
      fetchShares();
    } catch (err: any) {
      message.error('重置密码失败: ' + (err?.message || '未知错误'));
    } finally {
      setResetting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">免登录对外分享链接</h3>
          <p className="text-xs text-gray-500">
            生成受控、可轮换、可撤销的公开分享地址，无需账号直接在沙箱隔离环境预览
          </p>
        </div>
        {canManage && (
          <button
            onClick={handleOpenCreate}
            className="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white shadow-sm hover:bg-blue-700"
          >
            + 创建分享链接
          </button>
        )}
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-8 text-center text-sm text-gray-500">加载分享链接中...</div>
      ) : shares.length === 0 ? (
        <div className="rounded-lg border border-dashed p-8 text-center text-sm text-gray-500">
          暂无分享链接，点击右上角创建新的对外分享链接
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
            <thead className="bg-gray-50 text-xs font-semibold text-gray-500 uppercase">
              <tr>
                <th className="px-4 py-3">名称</th>
                <th className="px-4 py-3">状态</th>
                <th className="px-4 py-3">密码保护</th>
                <th className="px-4 py-3">说明范围</th>
                <th className="px-4 py-3">评论/附件</th>
                <th className="px-4 py-3">访问量</th>
                <th className="px-4 py-3">有效期</th>
                <th className="px-4 py-3 text-right">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {shares.map((s) => (
                <tr key={s.shareId} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-900">{s.name}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded px-2 py-0.5 text-xs font-semibold ${
                        s.status === 'ACTIVE'
                          ? 'bg-green-100 text-green-800'
                          : 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      {s.status === 'ACTIVE' ? '正常生效' : '已停用'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs">
                    {s.hasPassword ? '🔒 已设置密码' : '🔓 无需密码'}
                  </td>
                  <td className="px-4 py-3 text-xs">
                    {s.specScope === 'ALL'
                      ? '全部说明'
                      : s.specScope === 'CORE_FLOW'
                      ? '仅核心流程'
                      : '不公开'}
                  </td>
                  <td className="px-4 py-3 text-xs space-x-1">
                    {s.allowComment && <span className="rounded bg-indigo-50 text-indigo-700 px-1 py-0.5">评论</span>}
                    {s.allowPublicAttachment && <span className="rounded bg-teal-50 text-teal-700 px-1 py-0.5">公开附件</span>}
                    {!s.allowComment && !s.allowPublicAttachment && <span className="text-gray-400">-</span>}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">{s.visitCount} 次</td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    {s.expiresAt ? new Date(s.expiresAt).toLocaleString() : '永久有效'}
                  </td>
                  <td className="px-4 py-3 text-right space-x-2 text-xs">
                    {canManage && (
                      <>
                        <button
                          onClick={() => handleRotate(s)}
                          className="font-medium text-amber-600 hover:text-amber-800"
                        >
                          轮换 Token
                        </button>
                        <button
                          onClick={() => handleResetPassword(s)}
                          className="font-medium text-blue-600 hover:text-blue-800"
                        >
                          修改密码
                        </button>
                        <button
                          onClick={() => handleToggleStatus(s)}
                          className={`font-medium ${s.status === 'ACTIVE' ? 'text-red-600 hover:text-red-800' : 'text-green-600 hover:text-green-800'}`}
                        >
                          {s.status === 'ACTIVE' ? '停用' : '启用'}
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Creation Modal */}
      {createModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form
            onSubmit={handleCreateSubmit}
            className="w-full max-w-md rounded-xl bg-white p-6 shadow-xl space-y-4 text-gray-900 [color-scheme:light]"
          >
            <div className="flex items-center justify-between border-b pb-3">
              <h3 className="text-base font-semibold text-gray-900">创建对外分享链接</h3>
              <button
                type="button"
                onClick={() => setCreateModalOpen(false)}
                className="text-gray-400 hover:text-gray-600"
              >
                ✕
              </button>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700">链接名称 *</label>
              <input
                type="text"
                required
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                placeholder="例如：市场部评审、客户演示..."
                className="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs text-gray-900 placeholder:text-gray-400 shadow-sm focus:border-blue-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700">访问密码 (选填)</label>
              <input
                type="password"
                value={createForm.password || ''}
                onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
                placeholder="留空则无需密码即可访问"
                className="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs text-gray-900 placeholder:text-gray-400 shadow-sm focus:border-blue-500 focus:outline-none"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-700">结构化说明公开范围</label>
              <select
                value={createForm.specScope}
                onChange={(e) => setCreateForm({ ...createForm, specScope: e.target.value })}
                className="mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
              >
                <option value="NONE">不公开 (仅预览原型页面)</option>
                <option value="CORE_FLOW">仅公开核心流程</option>
                <option value="ALL">全部公开 (含功能目标、规则与约束)</option>
              </select>
            </div>

            <div className="space-y-2 pt-2">
              <label className="flex items-center gap-2 text-xs text-gray-700">
                <input
                  type="checkbox"
                  checked={createForm.allowComment}
                  onChange={(e) => setCreateForm({ ...createForm, allowComment: e.target.checked })}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                允许访客匿名/实名发表评论讨论
              </label>

              <label className="flex items-center gap-2 text-xs text-gray-700">
                <input
                  type="checkbox"
                  checked={createForm.allowPublicAttachment}
                  onChange={(e) => setCreateForm({ ...createForm, allowPublicAttachment: e.target.checked })}
                  className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                允许访客查看和下载标记为公开的附件素材
              </label>
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t">
              <button
                type="button"
                onClick={() => setCreateModalOpen(false)}
                className="rounded-md border border-gray-300 bg-white px-4 py-2 text-xs font-medium text-gray-700 hover:bg-gray-50"
              >
                取消
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="rounded-md bg-blue-600 px-4 py-2 text-xs font-medium text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
              >
                {submitting ? '生成中...' : '生成链接'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* One-time secret display modal */}
      <ShareSecretDialog
        open={Boolean(secretDialogUrl)}
        rawUrl={secretDialogUrl || ''}
        onClose={() => setSecretDialogUrl(null)}
      />

      {/* Password Reset Modal */}
      <Modal
        title={`重置访问密码 - ${resetTarget?.name || ''}`}
        open={Boolean(resetTarget)}
        onOk={handleConfirmResetPassword}
        onCancel={() => setResetTarget(null)}
        confirmLoading={resetting}
        okText="确认重置"
        cancelText="取消"
        destroyOnHidden
      >
        <div className="space-y-3 py-2">
          <p className="text-xs text-mute">
            请输入新的访问密码（若留空直接确定，则取消该分享链接的密码保护）：
          </p>
          <Input.Password
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="留空则无密码保护"
            maxLength={64}
          />
        </div>
      </Modal>
    </div>
  );
};
