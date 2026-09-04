import React, { useState } from 'react';
import { versionApi } from './api';

interface UploadVersionDialogProps {
  prototypeId: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (versionId: string) => void;
}

export const UploadVersionDialog: React.FC<UploadVersionDialogProps> = ({
  prototypeId,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [file, setFile] = useState<File | null>(null);
  const [changeLog, setChangeLog] = useState('');
  const [uploading, setUploading] = useState(false);
  const [stepMessage, setStepMessage] = useState('');
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = e.target.files?.[0];
    if (!selected) return;

    const lower = selected.name.toLowerCase();
    const isZip = lower.endsWith('.zip');
    const isHtml = lower.endsWith('.html') || lower.endsWith('.htm');

    if (!isZip && !isHtml) {
      setError('仅支持上传 .zip 压缩包或单个 .html 文件');
      setFile(null);
      return;
    }

    if (isHtml && selected.size > 10 * 1024 * 1024) {
      setError('HTML 文件大小不能超过 10MB');
      setFile(null);
      return;
    }

    if (isZip && selected.size > 100 * 1024 * 1024) {
      setError('ZIP 原型包大小不能超过 100MB');
      setFile(null);
      return;
    }

    setError(null);
    setFile(selected);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('请选择要上传的原型文件');
      return;
    }
    if (!changeLog.trim()) {
      setError('请输入本次版本的变更说明');
      return;
    }

    const fileType = file.name.toLowerCase().endsWith('.zip') ? 'ZIP' : 'HTML';

    try {
      setUploading(true);
      setError(null);

      // Step 1: Request presigned upload URL
      setStepMessage('正在获取安全直传授权...');
      const uploadResp = await versionApi.createUpload(file.name, file.size, fileType);

      // Step 2: Upload file directly to object storage via presigned URL
      setStepMessage('正在直传原型文件至对象存储...');
      await versionApi.directUpload(uploadResp.uploadUrl, file);

      // Step 3: Complete upload check
      setStepMessage('正在进行文件完整性与安全复核...');
      await versionApi.completeUpload(uploadResp.uploadId);

      // Step 4: Create prototype version and queue publish job
      setStepMessage('正在创建版本并提交发布任务...');
      const versionResp = await versionApi.createVersion(prototypeId, {
        uploadId: uploadResp.uploadId,
        sourceType: fileType,
        changeLog: changeLog.trim(),
      });

      onSuccess(versionResp.versionId);
      onClose();
    } catch (err: any) {
      setError(err?.message || '上传或发布失败，请重试');
    } finally {
      setUploading(false);
      setStepMessage('');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-lg rounded-xl bg-white p-6 text-gray-900 shadow-2xl [color-scheme:light]">
        <div className="flex items-center justify-between border-b pb-3">
          <h3 className="text-lg font-semibold text-gray-900">上传发布新版本</h3>
          {!uploading && (
            <button
              onClick={onClose}
              className="text-gray-400 hover:text-gray-600 focus:outline-none"
            >
              ✕
            </button>
          )}
        </div>

        {error && (
          <div className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">原型源文件 (.zip / .html)</label>
            <input
              type="file"
              accept=".zip,.html,.htm"
              disabled={uploading}
              onChange={handleFileChange}
              className="mt-1 block w-full text-sm text-gray-500 file:mr-4 file:rounded-md file:border-0 file:bg-blue-50 file:px-4 file:py-2 file:text-sm file:font-semibold file:text-blue-700 hover:file:bg-blue-100"
            />
            <p className="mt-1 text-xs text-gray-500">ZIP 限制 100MB（内含 index.html），HTML 单文件限制 10MB</p>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              变更说明 <span className="text-red-500">*</span>
            </label>
            <textarea
              required
              rows={3}
              maxLength={1000}
              disabled={uploading}
              value={changeLog}
              onChange={(e) => setChangeLog(e.target.value)}
              placeholder="请详细描述本次版本改动内容..."
              className="mt-1 block w-full rounded-md border border-gray-300 bg-white p-2 text-sm text-gray-900 placeholder:text-gray-400 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
            />
          </div>

          {uploading && (
            <div className="rounded-lg bg-blue-50 p-4 text-center">
              <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-blue-600 border-t-transparent mb-2" />
              <p className="text-sm font-medium text-blue-700">{stepMessage}</p>
            </div>
          )}

          <div className="flex justify-end gap-3 border-t pt-4">
            <button
              type="button"
              disabled={uploading}
              onClick={onClose}
              className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={uploading || !file}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {uploading ? '处理中...' : '确认上传并发布'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
