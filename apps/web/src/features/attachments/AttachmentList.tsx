import React, { useState } from 'react';
import { Modal, message } from 'antd';
import { AttachmentItem, attachmentApi } from './api';

interface AttachmentListProps {
  attachments: AttachmentItem[];
  canManage?: boolean;
  onRefresh?: () => void;
}

const ATTACHMENT_TYPE_MAP: Record<string, string> = {
  DOCUMENT: '文档',
  IMAGE: '图片',
  ICON: '图标',
  OTHER: '其他',
};

export const AttachmentList: React.FC<AttachmentListProps> = ({
  attachments,
  canManage = false,
  onRefresh,
}) => {
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const handleDownload = async (item: AttachmentItem) => {
    try {
      setDownloadingId(item.publicId);
      const ticket = await attachmentApi.getDownloadTicket(item.publicId);
      const a = document.createElement('a');
      a.href = ticket.url;
      a.download = item.name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } catch (e: any) {
      message.error('获取下载凭证失败: ' + (e?.message || '未知错误'));
    } finally {
      setDownloadingId(null);
    }
  };

  const handleDelete = (item: AttachmentItem) => {
    Modal.confirm({
      title: '确定要删除附件吗？',
      content: `附件 "${item.name}" 将被永久删除。`,
      okText: '确定删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          setDeletingId(item.publicId);
          await attachmentApi.delete(item.publicId);
          message.success('附件已删除');
          onRefresh?.();
        } catch (e: any) {
          message.error('删除附件失败: ' + (e?.message || '未知错误'));
        } finally {
          setDeletingId(null);
        }
      },
    });
  };

  if (attachments.length === 0) {
    return <div className="py-8 text-center text-sm text-gray-500">暂无附件资源</div>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
        <thead className="bg-gray-50 text-xs font-semibold text-gray-500 uppercase">
          <tr>
            <th className="px-4 py-3">名称</th>
            <th className="px-4 py-3">类型</th>
            <th className="px-4 py-3">用途</th>
            <th className="px-4 py-3">关联版本</th>
            <th className="px-4 py-3">可见范围</th>
            <th className="px-4 py-3">大小</th>
            <th className="px-4 py-3">上传人</th>
            <th className="px-4 py-3 text-right">操作</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white">
          {attachments.map((item) => (
            <tr key={item.publicId} className="hover:bg-gray-50">
              <td className="px-4 py-3 font-medium text-gray-900">
                <div className="flex items-center gap-2">
                  <span className="text-gray-400">
                    {item.type === 'IMAGE' ? '🖼️' : item.type === 'ICON' ? '🎨' : '📄'}
                  </span>
                  <span>{item.name}</span>
                </div>
              </td>
              <td className="px-4 py-3">
                <span className="rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
                  {ATTACHMENT_TYPE_MAP[item.type] || item.type}
                </span>
              </td>
              <td className="px-4 py-3 text-gray-600">{item.purpose || '-'}</td>
              <td className="px-4 py-3 text-gray-600">
                {item.versionNo ? `v${item.versionNo}` : '通用'}
              </td>
              <td className="px-4 py-3">
                <span
                  className={`rounded px-2 py-0.5 text-xs font-medium ${
                    item.accessScope === 'PUBLIC'
                      ? 'bg-green-100 text-green-800'
                      : 'bg-blue-100 text-blue-800'
                  }`}
                >
                  {item.accessScope === 'PUBLIC' ? '公开分享' : '内部可见'}
                </span>
              </td>
              <td className="px-4 py-3 text-gray-600">{formatSize(item.size)}</td>
              <td className="px-4 py-3 text-gray-600">{item.createdBy || '-'}</td>
              <td className="px-4 py-3 text-right space-x-2">
                <button
                  onClick={() => handleDownload(item)}
                  disabled={downloadingId === item.publicId}
                  className="text-xs font-medium text-blue-600 hover:text-blue-800 disabled:opacity-50"
                >
                  {downloadingId === item.publicId ? '下载中...' : '下载'}
                </button>
                {canManage && (
                  <button
                    onClick={() => handleDelete(item)}
                    disabled={deletingId === item.publicId}
                    className="text-xs font-medium text-red-600 hover:text-red-800 disabled:opacity-50"
                  >
                    {deletingId === item.publicId ? '删除中...' : '删除'}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
