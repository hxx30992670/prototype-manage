import React, { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Breadcrumb,
  Button,
  Card,
  Descriptions,
  Space,
  Spin,
  Tabs,
  Tag,
  Upload,
  Select,
  Input,
  message,
  Alert,
  Dropdown,
} from 'antd';
import type { UploadProps } from 'antd';
import { ArrowLeftOutlined, UploadOutlined, DownOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { prototypeApi, prototypeKeys, isPrototypeOwner, ownerNames } from './api';
import { getReviewStatusTag } from './status';
import { authApi } from '@/features/auth/api';
import { versionApi, versionKeys } from '@/features/versions/api';
import { VersionList } from '@/features/versions/VersionList';
import { PrototypePreview } from '@/features/versions/PrototypePreview';
import { PrototypeSpecEditor } from '@/features/specs/PrototypeSpecEditor';
import { ShareSettings } from '@/features/shares/ShareSettings';
import { CommentThread } from '@/features/comments/CommentThread';
import { attachmentApi } from '@/features/attachments/api';
import { AttachmentList } from '@/features/attachments/AttachmentList';

function versionStatusLabel(status?: string, versionNo?: number): string {
  if (!status) return '尚未发布版本';
  if (status === 'PUBLISHED') return versionNo != null ? `已发布 v${versionNo}` : '已发布';
  if (status === 'PUBLISHING') return '发布中';
  if (status === 'FAILED') return '发布失败';
  return status;
}

export const PrototypeDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('versions');

  const { data: currentUser } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.getCurrentUser(),
  });

  const { data: prototype, isLoading, error } = useQuery({
    queryKey: prototypeKeys.detail(id || ''),
    queryFn: () => prototypeApi.get(id!),
    enabled: Boolean(id),
  });

  const { data: versions = [] } = useQuery({
    queryKey: versionKeys.list(id || ''),
    queryFn: () => versionApi.list(id!),
    enabled: Boolean(id),
    refetchInterval: (query) =>
      query.state.data?.some((v) => v.status === 'PUBLISHING') ? 2000 : false,
  });

  const { data: attachments = [], refetch: refetchAttachments } = useQuery({
    queryKey: ['attachments', 'prototype', id],
    queryFn: () => attachmentApi.listForPrototype(id!),
    enabled: Boolean(id),
  });

  const isAdmin = currentUser?.roles.some((r) => r === 'ADMIN' || r === 'ROLE_ADMIN');
  const canManage = Boolean(
    currentUser &&
      prototype &&
      (isAdmin ||
        prototype.createdBy?.publicId === currentUser.publicId ||
        isPrototypeOwner(prototype, currentUser.publicId))
  );

  const currentVersion = versions.find((v) => v.isCurrent) ?? versions.find((v) => v.status === 'PUBLISHED');
  const publishingVersion = versions.find((v) => v.status === 'PUBLISHING');
  const readOnly = Boolean(prototype?.archived) || !canManage;

  const reviewStatusMutation = useMutation({
    mutationFn: (status: string) => prototypeApi.updateReviewStatus(id!, status),
    onSuccess: () => {
      message.success('评审状态更新成功');
      queryClient.invalidateQueries({ queryKey: prototypeKeys.detail(id!) });
      queryClient.invalidateQueries({ queryKey: prototypeKeys.all });
    },
    onError: (err: Error) => {
      message.error(err.message || '更新评审状态失败');
    },
  });

  if (!id) {
    return <Alert type="error" message="缺少原型 ID" />;
  }

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <Spin />
      </div>
    );
  }

  if (error || !prototype) {
    return (
      <Alert
        type="error"
        message="无法加载原型详情"
        description={(error as Error)?.message || '原型不存在或无权查看'}
        action={<Button onClick={() => navigate('/prototypes')}>返回列表</Button>}
      />
    );
  }

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/prototypes')}>原型库</a> },
          { title: prototype.name },
        ]}
      />

      <Card
        title={
          <Space>
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/prototypes')}>
              返回
            </Button>
            <span>{prototype.name}</span>
            <Tag>{prototype.code}</Tag>
            {prototype.archived && <Tag color="warning">已归档</Tag>}
          </Space>
        }
      >
        <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="分类">{prototype.category?.name || '-'}</Descriptions.Item>
          <Descriptions.Item label="评审状态">
            <Space size={6}>
              {getReviewStatusTag(prototype.reviewStatus)}
              {canManage && !prototype.archived && (
                <Dropdown
                  menu={{
                    items: [
                      { key: 'DRAFT', label: '设为草稿', onClick: () => reviewStatusMutation.mutate('DRAFT') },
                      { key: 'PENDING_REVIEW', label: '发起评审', onClick: () => reviewStatusMutation.mutate('PENDING_REVIEW') },
                      { key: 'APPROVED', label: '审核通过', onClick: () => reviewStatusMutation.mutate('APPROVED') },
                      { key: 'REJECTED', label: '审核驳回', onClick: () => reviewStatusMutation.mutate('REJECTED') },
                    ],
                  }}
                >
                  <Button type="link" size="small" style={{ padding: 0, fontSize: 12 }}>
                    修改状态 <DownOutlined />
                  </Button>
                </Dropdown>
              )}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="版本状态">
            {versionStatusLabel(prototype.currentVersionStatus, prototype.currentVersionNo)}
          </Descriptions.Item>
          <Descriptions.Item label="负责人">{ownerNames(prototype)}</Descriptions.Item>
          <Descriptions.Item label="创建人">{prototype.createdBy?.displayName || '-'}</Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {prototype.updatedAt ? new Date(prototype.updatedAt).toLocaleString() : '-'}
          </Descriptions.Item>
        </Descriptions>

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'preview',
              label: '预览',
              children: currentVersion ? (
                <PrototypePreview
                  key={currentVersion.versionId}
                  prototypeId={prototype.publicId}
                  versionId={currentVersion.versionId}
                  title={`${prototype.name} v${currentVersion.versionNo}`}
                />
              ) : publishingVersion ? (
                <Alert type="info" message="版本正在发布中，完成后即可预览" />
              ) : (
                <Alert type="info" message="尚未发布版本，无法预览" />
              ),
            },
            {
              key: 'versions',
              label: '版本',
              children: (
                <VersionList
                  prototypeId={prototype.publicId}
                  canManage={canManage && !prototype.archived}
                />
              ),
            },
            {
              key: 'spec',
              label: '说明与约束',
              children: (
                <PrototypeSpecEditor prototypeId={prototype.publicId} readOnly={readOnly} />
              ),
            },
            {
              key: 'attachments',
              label: '附件',
              children: (
                <AttachmentPanel
                  prototypeId={prototype.publicId}
                  versionPublicId={currentVersion?.versionId}
                  attachments={attachments}
                  canManage={canManage && !prototype.archived}
                  onRefresh={() => {
                    refetchAttachments();
                    queryClient.invalidateQueries({ queryKey: prototypeKeys.detail(prototype.publicId) });
                  }}
                />
              ),
            },
            {
              key: 'comments',
              label: '评论',
              children: currentVersion ? (
                <CommentThread
                  prototypeId={prototype.publicId}
                  currentVersionId={currentVersion.versionId}
                  currentVersionNo={currentVersion.versionNo}
                  canManage={canManage}
                />
              ) : (
                <Alert type="info" message="发布版本后可进行评论" />
              ),
            },
            {
              key: 'shares',
              label: '分享设置',
              children: (
                <ShareSettings prototypeId={prototype.publicId} canManage={canManage && !prototype.archived} />
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
};

const AttachmentPanel: React.FC<{
  prototypeId: string;
  versionPublicId?: string;
  attachments: Awaited<ReturnType<typeof attachmentApi.listForPrototype>>;
  canManage: boolean;
  onRefresh: () => void;
}> = ({ prototypeId, versionPublicId, attachments, canManage, onRefresh }) => {
  const [name, setName] = useState('');
  const [type, setType] = useState('DOCUMENT');
  const [accessScope, setAccessScope] = useState('INTERNAL');
  const [uploading, setUploading] = useState(false);

  const coverMutation = useMutation({
    mutationFn: async (file: File) => {
      const upload = await attachmentApi.createUpload(file.name, file.size, 'COVER');
      await attachmentApi.directUpload(upload.uploadUrl, file);
      await attachmentApi.completeUpload(upload.uploadId);
      await attachmentApi.uploadCover(prototypeId, upload.uploadId);
    },
    onSuccess: () => {
      message.success('封面已更新');
      onRefresh();
    },
    onError: (err: Error) => message.error(err.message || '封面上传失败'),
  });

  const handleUpload: UploadProps['customRequest'] = async (options) => {
    const file = options.file as File;
    if (!name.trim()) {
      message.error('请先填写附件名称');
      options.onError?.(new Error('missing name'));
      return;
    }
    try {
      setUploading(true);
      const upload = await attachmentApi.createUpload(file.name, file.size, 'ATTACHMENT');
      await attachmentApi.directUpload(upload.uploadUrl, file);
      await attachmentApi.completeUpload(upload.uploadId);
      await attachmentApi.create(prototypeId, {
        uploadId: upload.uploadId,
        name: name.trim(),
        type,
        accessScope,
        versionPublicId,
      });
      message.success('附件已添加');
      setName('');
      onRefresh();
      options.onSuccess?.({}, file);
    } catch (err) {
      const error = err as Error;
      message.error(error.message || '附件上传失败');
      options.onError?.(error);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      {canManage && (
        <Space direction="vertical" style={{ width: '100%', marginBottom: 16 }} size="middle">
          <Space wrap>
            <Input
              placeholder="附件名称"
              value={name}
              onChange={(e) => setName(e.target.value)}
              style={{ width: 220 }}
            />
            <Select
              value={type}
              onChange={setType}
              style={{ width: 140 }}
              options={[
                { value: 'DOCUMENT', label: '文档' },
                { value: 'IMAGE', label: '图片' },
                { value: 'ICON', label: '图标' },
                { value: 'OTHER', label: '其他' },
              ]}
            />
            <Select
              value={accessScope}
              onChange={setAccessScope}
              style={{ width: 140 }}
              options={[
                { value: 'INTERNAL', label: '内部' },
                { value: 'PUBLIC', label: '公开可下载' },
              ]}
            />
            <Upload showUploadList={false} customRequest={handleUpload} disabled={uploading}>
              <Button icon={<UploadOutlined />} loading={uploading}>
                上传附件
              </Button>
            </Upload>
            <Upload
              showUploadList={false}
              accept=".png,.jpg,.jpeg,.webp"
              customRequest={(options) => coverMutation.mutate(options.file as File)}
            >
              <Button loading={coverMutation.isPending}>设置封面</Button>
            </Upload>
          </Space>
        </Space>
      )}
      <AttachmentList attachments={attachments} canManage={canManage} onRefresh={onRefresh} />
    </div>
  );
};
