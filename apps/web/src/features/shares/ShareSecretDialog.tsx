import React, { useState } from 'react';
import { message } from 'antd';

interface ShareSecretDialogProps {
  open: boolean;
  rawUrl: string;
  onClose: () => void;
}

export const ShareSecretDialog: React.FC<ShareSecretDialogProps> = ({
  open,
  rawUrl,
  onClose,
}) => {
  const [copied, setCopied] = useState(false);

  if (!open) return null;

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(rawUrl);
      setCopied(true);
      message.success('已复制分享链接到剪贴板');
      setTimeout(() => setCopied(false), 2000);
    } catch {
      message.warning('复制失败，请手动选择复制');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-lg rounded-xl bg-white p-6 text-gray-900 shadow-xl [color-scheme:light]">
        <div className="flex items-center justify-between border-b pb-3">
          <h3 className="text-lg font-semibold text-gray-900">分享链接创建成功</h3>
        </div>

        <div className="mt-4 space-y-4">
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900">
            <strong>⚠️ 安全提醒：</strong>
            完整包含 Token 的访问链接<strong>仅在此处展示一次</strong>。系统数据库仅存储单向哈希摘要，弹窗关闭后将无法重新查看该链接，请立即复制保存！
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700">公开访问地址</label>
            <div className="mt-1 flex gap-2">
              <input
                type="text"
                readOnly
                value={rawUrl}
                aria-label="公开访问地址"
                className="w-full rounded-md border border-gray-300 bg-gray-50 px-3 py-2 text-xs font-mono text-gray-800 shadow-sm focus:outline-none"
              />
              <button
                onClick={handleCopy}
                className="shrink-0 rounded-md bg-blue-600 px-4 py-2 text-xs font-medium text-white shadow hover:bg-blue-700"
              >
                {copied ? '已复制！' : '复制链接'}
              </button>
            </div>
          </div>
        </div>

        <div className="mt-6 flex justify-end">
          <button
            onClick={onClose}
            className="rounded-md border border-gray-300 bg-white px-4 py-2 text-xs font-medium text-gray-700 shadow-sm hover:bg-gray-50"
          >
            我已复制，关闭窗口
          </button>
        </div>
      </div>
    </div>
  );
};
