import React, { useEffect, useState } from 'react';
import { AttachmentItem, attachmentApi } from './api';
import { AttachmentList } from './AttachmentList';

export const AttachmentLibraryPage: React.FC = () => {
  const [attachments, setAttachments] = useState<AttachmentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState<string>('');
  const [error, setError] = useState<string | null>(null);

  const fetchAttachments = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await attachmentApi.listGlobal({
        type: typeFilter || undefined,
      });
      setAttachments(data);
    } catch (e: any) {
      setError(e?.message || '获取附件库失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAttachments();
  }, [typeFilter]);

  return (
    <div className="space-y-6 text-gray-900 [color-scheme:light]">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">全局附件与素材库</h2>
          <p className="text-xs text-gray-500">统一管理所有已授权原型的相关文档、设计素材与规范说明</p>
        </div>

        <div className="flex items-center gap-3">
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-xs text-gray-900 shadow-sm focus:border-blue-500 focus:outline-none"
          >
            <option value="">全部类型</option>
            <option value="IMAGE">图片 (IMAGE)</option>
            <option value="DOCUMENT">文档 (DOCUMENT)</option>
            <option value="ICON">图标 (ICON)</option>
            <option value="OTHER">其他 (OTHER)</option>
          </select>

          <button
            onClick={fetchAttachments}
            className="rounded-md bg-white border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50 shadow-sm"
          >
            刷新
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-12 text-center text-sm text-gray-500">正在加载附件库...</div>
      ) : (
        <div className="rounded-lg border bg-white shadow-sm">
          <AttachmentList attachments={attachments} onRefresh={fetchAttachments} />
        </div>
      )}
    </div>
  );
};
