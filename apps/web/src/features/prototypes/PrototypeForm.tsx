import React, { useEffect, useMemo } from 'react';
import { Modal, Form, Input, Select, message } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { prototypeApi, PrototypeItem, CreatePrototypePayload, UpdatePrototypePayload, prototypeKeys, prototypeOwners } from './api';
import { authApi } from '@/features/auth/api';

interface PrototypeFormProps {
  open: boolean;
  prototype?: PrototypeItem | null;
  onClose: () => void;
}

export const PrototypeForm: React.FC<PrototypeFormProps> = ({ open, prototype, onClose }) => {
  const [form] = Form.useForm();
  const queryClient = useQueryClient();
  const isEditing = !!prototype;
  const visibility = Form.useWatch('visibility', form);
  const downloadAccess = Form.useWatch('downloadAccess', form);
  // 条件挂载前也读取已初始化的查看人员，避免将下载授权误判为空。
  const viewerIds = Form.useWatch('viewerIds', { form, preserve: true }) as string[] | undefined;
  const ownerIds = Form.useWatch('ownerIds', form) as string[] | undefined;

  const { data: categories = [] } = useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: () => prototypeApi.listCategories(),
  });

  const { data: tags = [] } = useQuery({
    queryKey: ['catalog', 'tags'],
    queryFn: () => prototypeApi.listTags(),
  });

  const { data: currentUser } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.getCurrentUser(),
  });

  const { data: users = [] } = useQuery({
    queryKey: ['users', 'assignable-owners'],
    enabled: open,
    queryFn: () => prototypeApi.listAssignableOwners(),
  });

  const { data: members = [], isSuccess: membersLoaded } = useQuery({
    queryKey: ['users', 'active'],
    enabled: open && (visibility === 'RESTRICTED' || downloadAccess === 'SELECTED'),
    queryFn: () => prototypeApi.listActiveUsers(),
  });

  const downloadCandidates = useMemo(() => {
    const excluded = new Set(ownerIds ?? []);
    const creatorId = prototype?.createdBy?.publicId ?? currentUser?.publicId;
    if (creatorId) excluded.add(creatorId);
    return members.filter((member) => {
      if (excluded.has(member.publicId)) return false;
      if (visibility === 'RESTRICTED') return (viewerIds ?? []).includes(member.publicId);
      return true;
    });
  }, [members, ownerIds, prototype, currentUser, visibility, viewerIds]);

  useEffect(() => {
    if (open) {
      if (prototype) {
        form.setFieldsValue({
          code: prototype.code,
          name: prototype.name,
          categoryId: prototype.category?.code,
          ownerIds: prototypeOwners(prototype).map((owner) => owner.publicId),
          visibility: prototype.visibility,
          viewerIds: (prototype.viewers ?? []).map((viewer) => viewer.publicId),
          downloadAccess: prototype.downloadAccess || 'MANAGERS_ONLY',
          downloaderIds: (prototype.downloaders ?? []).map((person) => person.publicId),
          description: prototype.description,
          publicSummary: prototype.publicSummary,
          tagIds: prototype.tags?.map((t) => t.name) || [],
        });
      } else {
        form.resetFields();
        form.setFieldsValue({
          visibility: 'ALL_INTERNAL',
          ownerIds: currentUser?.publicId ? [currentUser.publicId] : [],
          viewerIds: [],
          downloadAccess: 'MANAGERS_ONLY',
          downloaderIds: [],
        });
      }
    }
  }, [open, prototype, form, currentUser]);

  useEffect(() => {
    if (!open || downloadAccess !== 'SELECTED' || !membersLoaded) return;
    const allowed = new Set(downloadCandidates.map((member) => member.publicId));
    const current = form.getFieldValue('downloaderIds');
    if (!Array.isArray(current) || current.length === 0) return;
    const next = current.filter((id: string) => allowed.has(id));
    if (next.length !== current.length) {
      form.setFieldValue('downloaderIds', next);
    }
  }, [open, downloadAccess, membersLoaded, downloadCandidates, form]);

  const createMutation = useMutation({
    mutationFn: (values: CreatePrototypePayload) => prototypeApi.create(values),
    onSuccess: () => {
      message.success('原型创建成功');
      queryClient.invalidateQueries({ queryKey: prototypeKeys.all });
      onClose();
    },
    onError: (err: Error) => {
      message.error(err.message || '创建原型失败');
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: UpdatePrototypePayload) => {
      if (!prototype) throw new Error('未选择原型');
      return prototypeApi.update(prototype.publicId, values, prototype.rowVersion);
    },
    onSuccess: () => {
      message.success('原型更新成功');
      queryClient.invalidateQueries({ queryKey: prototypeKeys.all });
      onClose();
    },
    onError: (err: Error) => {
      message.error(err.message || '更新原型失败');
    },
  });

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      values.ownerIds = Array.isArray(values.ownerIds) ? values.ownerIds : [];
      values.viewerIds = values.visibility === 'RESTRICTED' && Array.isArray(values.viewerIds)
        ? values.viewerIds
        : [];
      values.downloadAccess = values.downloadAccess || 'MANAGERS_ONLY';
      if (values.downloadAccess !== 'SELECTED') {
        values.downloaderIds = [];
      } else if (membersLoaded) {
        const allowed = new Set(downloadCandidates.map((member) => member.publicId));
        values.downloaderIds = Array.isArray(values.downloaderIds)
          ? values.downloaderIds.filter((id: string) => allowed.has(id))
          : [];
      } else if (!Array.isArray(values.downloaderIds)) {
        values.downloaderIds = [];
      }
      if (isEditing) {
        updateMutation.mutate(values);
      } else {
        createMutation.mutate(values);
      }
    } catch {
      // Form validation error
    }
  };

  return (
    <Modal
      title={isEditing ? '编辑原型资料' : '新建原型'}
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      confirmLoading={createMutation.isPending || updateMutation.isPending}
      okText="保存"
      cancelText="取消"
      width={600}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="code"
          label="原型编码"
          rules={[
            { required: true, message: '请输入原型编码' },
            { pattern: /^[A-Za-z0-9_-]{2,50}$/, message: '2-50位字母、数字、下划线或连字符' },
          ]}
        >
          <Input placeholder="例如: trade-settle-v1" disabled={isEditing} />
        </Form.Item>

        <Form.Item
          name="name"
          label="原型名称"
          rules={[
            { required: true, message: '请输入原型名称' },
            { min: 2, max: 100, message: '长度在2到100个字符' },
          ]}
        >
          <Input placeholder="例如: 智能交易结算系统" />
        </Form.Item>

        <Form.Item
          name="categoryId"
          label="所属分类"
          rules={[{ required: true, message: '请选择所属分类' }]}
        >
          <Select
            placeholder="选择分类"
            options={categories.map((c) => ({ label: c.name, value: c.code }))}
          />
        </Form.Item>

        <Form.Item
          name="ownerIds"
          label="负责人"
          extra="可多选，被选中的创建者都能编辑原型并上传版本"
          rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一名负责人' }]}
        >
          <Select
            mode="multiple"
            placeholder="选择一名或多名负责人"
            options={users.map((u) => ({ label: `${u.displayName} (${u.username})`, value: u.publicId }))}
            showSearch
            optionFilterProp="label"
            maxCount={20}
          />
        </Form.Item>

        <Form.Item
          name="visibility"
          label="可见范围"
          extra={visibility === 'RESTRICTED'
            ? '受限后，只有负责人、创建者、管理员，以及下面选中的人能查看。'
            : '全员可见和公开时，所有已登录的内部用户都能查看。'}
        >
          <Select
            options={[
              { label: '全员可见', value: 'ALL_INTERNAL' },
              { label: '公开', value: 'PUBLIC' },
              { label: '受限访问', value: 'RESTRICTED' },
            ]}
          />
        </Form.Item>

        {visibility === 'RESTRICTED' && (
          <Form.Item
            name="viewerIds"
            label="可查看的人"
            extra="不选其他人时，只有负责人、创建者和管理员能查看。负责人始终可以查看和编辑。"
          >
            <Select
              mode="multiple"
              placeholder="选择可以查看该原型的人"
              options={members.map((member) => ({
                label: `${member.displayName} (${member.username})`,
                value: member.publicId,
              }))}
              showSearch
              optionFilterProp="label"
              maxCount={50}
            />
          </Form.Item>
        )}

        <Form.Item
          name="downloadAccess"
          label="下载权限"
          extra="负责人、创建者和管理员始终可以下载源文件。这里决定是否再开放给其他能看见该原型的人。下载的是上传时的 HTML 或 ZIP。"
        >
          <Select
            options={[
              { label: '仅负责人、创建者和管理员', value: 'MANAGERS_ONLY' },
              { label: '所有能看见的人', value: 'ALL_VIEWERS' },
              { label: '指定部分能看见的人', value: 'SELECTED' },
            ]}
          />
        </Form.Item>

        {downloadAccess === 'SELECTED' && (
          <Form.Item
            name="downloaderIds"
            label="可下载的人"
            extra={visibility === 'RESTRICTED'
              ? '只能从当前可查看的人里选。不选任何人时，只有负责人、创建者和管理员能下载。'
              : '只能从当前能看见该原型的人里选。不选任何人时，只有负责人、创建者和管理员能下载。'}
          >
            <Select
              mode="multiple"
              placeholder={visibility === 'RESTRICTED' && downloadCandidates.length === 0
                ? '请先选择可查看的人'
                : '选择可以下载源文件的人'}
              options={downloadCandidates.map((member) => ({
                label: `${member.displayName} (${member.username})`,
                value: member.publicId,
              }))}
              showSearch
              optionFilterProp="label"
              maxCount={50}
              disabled={visibility === 'RESTRICTED' && downloadCandidates.length === 0}
            />
          </Form.Item>
        )}

        <Form.Item name="tagIds" label="标签">
          <Select
            mode="tags"
            placeholder="选择或输入标签"
            options={tags.map((t) => ({ label: t.name, value: t.name }))}
          />
        </Form.Item>

        <Form.Item name="description" label="原型描述">
          <Input.TextArea rows={3} placeholder="简要描述原型功能与受众" maxLength={500} showCount />
        </Form.Item>

        <Form.Item name="publicSummary" label="公开摘要 (脱敏展示)">
          <Input.TextArea rows={2} placeholder="对外或跨团队公开摘要" maxLength={2000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
};
