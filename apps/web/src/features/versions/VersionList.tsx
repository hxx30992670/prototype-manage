import React, { useState, useEffect, useCallback } from 'react';
import { Modal, Input, message } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { versionApi, versionKeys, VersionItem, PublishJobStatus } from './api';
import { prototypeKeys } from '@/features/prototypes/api';
import { UploadVersionDialog } from './UploadVersionDialog';

interface VersionListProps {
  prototypeId: string;
  canManage?: boolean;
}

const STAGE_LABEL_MAP: Record<string, string> = {
  UPLOAD_CHECK: '上传校验',
  PACKAGE_CHECK: '文件包校验',
  EXTRACT: '解压文件',
  STORE: '存储同步',
  FINALIZE: '完成发布',
};

export const VersionList: React.FC<VersionListProps> = ({
  prototypeId,
  canManage = false,
}) => {
  const queryClient = useQueryClient();
  const [isUploadOpen, setIsUploadOpen] = useState(false);

  // Switch modal state
  const [switchTarget, setSwitchTarget] = useState<VersionItem | null>(null);
  const [switchReason, setSwitchReason] = useState('');
  const [switching, setSwitching] = useState(false);

  // Polling for publishing versions
  const [jobs, setJobs] = useState<Record<string, PublishJobStatus>>({});

  const {
    data: versions = [],
    isLoading: loading,
    error: queryError,
  } = useQuery({
    queryKey: versionKeys.list(prototypeId),
    queryFn: () => versionApi.list(prototypeId),
  });

  const error = queryError
    ? ((queryError as { message?: string }).message || '获取版本列表失败')
    : null;

  const loadVersions = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: versionKeys.list(prototypeId) });
    await queryClient.invalidateQueries({ queryKey: prototypeKeys.detail(prototypeId) });
  }, [queryClient, prototypeId]);

  // Poll publishing versions
  useEffect(() => {
    const publishingVersions = versions.filter((v) => v.status === 'PUBLISHING');
    if (publishingVersions.length === 0) return;

    const interval = setInterval(async () => {
      let anyChanged = false;
      const updatedJobs: Record<string, PublishJobStatus> = {};

      for (const v of publishingVersions) {
        try {
          const job = await versionApi.getPublishJob(prototypeId, v.versionId);
          updatedJobs[v.versionId] = job;
          if (job.status === 'SUCCEEDED' || job.status === 'FAILED') {
            anyChanged = true;
          }
        } catch {
          // ignore transient poll error
        }
      }

      setJobs(updatedJobs);
      if (anyChanged) {
        loadVersions();
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [versions, prototypeId, loadVersions]);

  const handleSwitchCurrent = async () => {
    if (!switchTarget || !switchReason.trim()) return;
    try {
      setSwitching(true);
      const current = versions.find((v) => v.isCurrent);
      await versionApi.switchCurrent(prototypeId, switchTarget.versionId, {
        expectedCurrentVersionNo: current?.versionNo,
        reason: switchReason.trim(),
      });
      message.success('生效版本切换成功');
      setSwitchTarget(null);
      setSwitchReason('');
      await loadVersions();
    } catch (err: any) {
      message.error(err?.message || '切换版本失败');
    } finally {
      setSwitching(false);
    }
  };

  const handleDownload = async (version: VersionItem) => {
    try {
      const ticket = await versionApi.createDownloadTicket(prototypeId, version.versionId);
      window.open(ticket.url, '_blank');
    } catch (err: any) {
      message.error(err?.message || '获取下载链接失败');
    }
  };

  const handleDelete = (version: VersionItem) => {
    Modal.confirm({
      title: `确定删除版本 v${version.versionNo} 吗？`,
      content: '删除后该版本的原型静态资源将不可恢复。',
      okText: '确定删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await versionApi.delete(prototypeId, version.versionId);
          message.success('版本已删除');
          await loadVersions();
        } catch (err: any) {
          message.error(err?.message || '删除版本失败');
        }
      },
    });
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900">版本历史记录</h3>
        {canManage && (
          <button
            onClick={() => setIsUploadOpen(true)}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow hover:bg-blue-700"
          >
            发布新版本
          </button>
        )}
      </div>

      {error && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-8 text-center text-sm text-gray-500">加载版本列表中...</div>
      ) : versions.length === 0 ? (
        <div className="rounded-lg border-2 border-dashed border-gray-200 py-12 text-center text-gray-500">
          暂无版本，点击上方按钮发布首个版本
        </div>
      ) : (
        <div className="divide-y rounded-lg border bg-white shadow-sm">
          {versions.map((ver) => {
            const job = jobs[ver.versionId];
            return (
              <div key={ver.versionId} className="p-4 transition hover:bg-gray-50/50">
                <div className="flex items-start justify-between">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-base font-bold text-gray-900">
                        v{ver.versionNo}
                      </span>
                      {ver.isCurrent && (
                        <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-semibold text-green-800">
                          当前生效
                        </span>
                      )}
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          ver.status === 'PUBLISHED'
                            ? 'bg-blue-50 text-blue-700'
                            : ver.status === 'PUBLISHING'
                            ? 'bg-yellow-50 text-yellow-700'
                            : 'bg-red-50 text-red-700'
                        }`}
                      >
                        {ver.status === 'PUBLISHED'
                          ? '已发布'
                          : ver.status === 'PUBLISHING'
                          ? '发布中'
                          : '发布失败'}
                      </span>
                      <span className="text-xs text-gray-400">
                        {ver.sourceType} • {formatBytes(ver.sourceSize)}
                      </span>
                    </div>

                    <p className="text-sm text-gray-700">{ver.changeLog}</p>

                    <div className="flex items-center gap-4 text-xs text-gray-500">
                      {ver.createdBy && <span>发布者: {ver.createdBy}</span>}
                      <span>提交时间: {new Date(ver.createdAt).toLocaleString()}</span>
                      {ver.status === 'PUBLISHED' && ver.fileCount > 0 && (
                        <span>解压文件: {ver.fileCount} 个 ({formatBytes(ver.expandedSize)})</span>
                      )}
                    </div>

                    {ver.status === 'PUBLISHING' && job && (
                      <div className="mt-2 w-72 rounded bg-yellow-50 p-2">
                        <div className="flex justify-between text-xs text-yellow-800">
                          <span>阶段: {job.stage ? (STAGE_LABEL_MAP[job.stage] || job.stage) : '处理中'}</span>
                          <span>{job.progress}%</span>
                        </div>
                        <div className="mt-1 h-1.5 w-full rounded-full bg-yellow-200">
                          <div
                            className="h-1.5 rounded-full bg-yellow-600 transition-all duration-300"
                            style={{ width: `${job.progress}%` }}
                          />
                        </div>
                      </div>
                    )}

                    {ver.status === 'FAILED' && (
                      <div className="mt-2 rounded bg-red-50 p-2 text-xs text-red-700">
                        <span className="font-semibold">
                          失败阶段 [{ver.failureStage ? (STAGE_LABEL_MAP[ver.failureStage] || ver.failureStage) : '未知'}]:
                        </span>{' '}
                        {ver.failureMessage || '解析或校验失败'}
                      </div>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    {canManage && ver.status === 'PUBLISHED' && !ver.isCurrent && (
                      <button
                        onClick={() => setSwitchTarget(ver)}
                        className="rounded border border-gray-300 bg-white px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50"
                      >
                        回滚至此版本
                      </button>
                    )}

                    {canManage && (
                      <button
                        onClick={() => handleDownload(ver)}
                        className="rounded border border-gray-300 bg-white px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-50"
                      >
                        下载源文件
                      </button>
                    )}

                    {canManage && ver.status === 'FAILED' && (
                      <button
                        onClick={() => handleDelete(ver)}
                        className="rounded border border-red-200 bg-white px-3 py-1 text-xs font-medium text-red-600 hover:bg-red-50"
                      >
                        删除
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Upload Dialog */}
      <UploadVersionDialog
        prototypeId={prototypeId}
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onSuccess={() => loadVersions()}
      />

      {/* Switch Version Dialog */}
      <Modal
        title={`确认切换生效版本至 v${switchTarget?.versionNo || ''}`}
        open={Boolean(switchTarget)}
        onOk={handleSwitchCurrent}
        onCancel={() => {
          setSwitchTarget(null);
          setSwitchReason('');
        }}
        confirmLoading={switching}
        okButtonProps={{ disabled: !switchReason.trim() }}
        okText="确认切换"
        cancelText="取消"
        destroyOnHidden
      >
        <div className="space-y-3 py-2">
          <p className="text-xs text-mute">
            切换后，用户访问该原型预览地址将直接展示 v{switchTarget?.versionNo} 的内容。
          </p>
          <div>
            <label className="block text-xs font-semibold text-ink mb-1">
              切换原因 <span className="text-red-500">*</span>
            </label>
            <Input.TextArea
              rows={3}
              value={switchReason}
              onChange={(e) => setSwitchReason(e.target.value)}
              placeholder="请填写版本回滚或切换原因..."
              maxLength={200}
              showCount
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};
