import React, { useEffect } from 'react';
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

  useEffect(() => {
    if (open) {
      if (prototype) {
        form.setFieldsValue({
          code: prototype.code,
          name: prototype.name,
          categoryId: prototype.category?.code,
          ownerIds: prototypeOwners(prototype).map((owner) => owner.publicId),
          visibility: prototype.visibility,
          description: prototype.description,
          publicSummary: prototype.publicSummary,
          tagIds: prototype.tags?.map((t) => t.name) || [],
        });
      } else {
        form.resetFields();
        form.setFieldsValue({
          visibility: 'ALL_INTERNAL',
          ownerIds: currentUser?.publicId ? [currentUser.publicId] : [],
        });
      }
    }
  }, [open, prototype, form, currentUser]);

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

        <Form.Item name="visibility" label="可见范围">
          <Select
            options={[
              { label: '全员可见 (ALL_INTERNAL)', value: 'ALL_INTERNAL' },
              { label: '公开 (PUBLIC)', value: 'PUBLIC' },
              { label: '受限访问 (RESTRICTED)', value: 'RESTRICTED' },
            ]}
          />
        </Form.Item>

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
