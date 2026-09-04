import React, { useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Tag, message, Space, Switch } from 'antd';
import { PlusOutlined, EditOutlined, KeyOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from '@/lib/http';

interface UserRecord {
  publicId: string;
  username: string;
  displayName: string;
  department?: string;
  status: 'ACTIVE' | 'DISABLED';
  roles: string[];
  mustChangePassword: boolean;
  lastLoginAt?: string;
  createdAt: string;
  protectedAccount?: boolean;
}

const ROLE_LABEL_MAP: Record<string, string> = {
  ADMIN: '系统管理员',
  ROLE_ADMIN: '系统管理员',
  CREATOR: '原型创建者',
  ROLE_CREATOR: '原型创建者',
  VIEWER: '普通查看者',
  ROLE_VIEWER: '普通查看者',
};

const ROLE_OPTIONS = [
  { label: 'ADMIN (系统管理员)', value: 'ADMIN' },
  { label: 'CREATOR (原型创建者)', value: 'CREATOR' },
  { label: 'VIEWER (普通查看者)', value: 'VIEWER' },
];

function normalizeRole(role: string): string {
  return role.replace(/^ROLE_/, '');
}

export const UserAdminPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();

  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null);
  const [editForm] = Form.useForm();

  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [resetUserId, setResetUserId] = useState<string | null>(null);
  const [resetForm] = Form.useForm();

  const { data: users = [], isLoading } = useQuery({
    queryKey: ['admin', 'users', 'list'],
    queryFn: async () => {
      const res = await http.get<{ data: UserRecord[] }>('/admin/users');
      return res.data.data;
    },
  });

  const createUserMutation = useMutation({
    mutationFn: async (values: unknown) => {
      await http.post('/admin/users', values);
    },
    onSuccess: () => {
      message.success('用户创建成功');
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      setCreateModalOpen(false);
      createForm.resetFields();
    },
    onError: (err: Error) => {
      message.error(err.message || '创建用户失败');
    },
  });

  const updateUserMutation = useMutation({
    mutationFn: async ({ id, values }: { id: string; values: unknown }) => {
      await http.put(`/admin/users/${id}`, values);
    },
    onSuccess: () => {
      message.success('用户资料更新成功');
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      setEditModalOpen(false);
    },
    onError: (err: Error) => {
      message.error(err.message || '更新失败');
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: 'ACTIVE' | 'DISABLED' }) => {
      await http.put(`/admin/users/${id}/status`, { status });
    },
    onSuccess: (_, variables) => {
      message.success(variables.status === 'ACTIVE' ? '用户已启用' : '用户已禁用，会话已注销');
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
    onError: (err: Error) => {
      message.error(err.message || '更新状态失败');
    },
  });

  const resetPasswordMutation = useMutation({
    mutationFn: async ({ id, newPassword }: { id: string; newPassword: string }) => {
      await http.post(`/admin/users/${id}/reset-password`, { newPassword });
    },
    onSuccess: () => {
      message.success('密码已成功重置，用户下次登录需修改密码');
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
      setResetModalOpen(false);
      resetForm.resetFields();
    },
    onError: (err: Error) => {
      message.error(err.message || '重置密码失败');
    },
  });

  const openEdit = (record: UserRecord) => {
    setEditingUser(record);
    editForm.setFieldsValue({
      displayName: record.displayName,
      department: record.department,
      roles: (record.roles || []).map(normalizeRole),
    });
    setEditModalOpen(true);
  };

  const openReset = (publicId: string) => {
    setResetUserId(publicId);
    resetForm.resetFields();
    setResetModalOpen(true);
  };

  return (
    <Card
      title="用户与角色管理"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
          新建用户
        </Button>
      }
    >
      <Table
        rowKey="publicId"
        loading={isLoading}
        dataSource={users}
        locale={{ emptyText: '暂无系统用户' }}
        columns={[
          {
            title: '用户名',
            dataIndex: 'username',
            render: (username: string, record) => (
              <Space>
                <span>{username}</span>
                {record.protectedAccount ? <Tag color="gold">内置账号</Tag> : null}
              </Space>
            ),
          },
          { title: '姓名', dataIndex: 'displayName' },
          { title: '部门', dataIndex: 'department', render: (d) => d || '-' },
          {
            title: '角色',
            dataIndex: 'roles',
            render: (roles: string[]) =>
              roles.map((r) => (
                <Tag color={r === 'ADMIN' || r === 'ROLE_ADMIN' ? 'gold' : r === 'CREATOR' || r === 'ROLE_CREATOR' ? 'blue' : 'default'} key={r}>
                  {ROLE_LABEL_MAP[r] || r}
                </Tag>
              )),
          },
          {
            title: '状态',
            dataIndex: 'status',
            render: (status: 'ACTIVE' | 'DISABLED', record) => (
              <Switch
                checked={status === 'ACTIVE'}
                checkedChildren="正常"
                unCheckedChildren="已禁用"
                disabled={record.protectedAccount}
                onChange={(checked) =>
                  updateStatusMutation.mutate({
                    id: record.publicId,
                    status: checked ? 'ACTIVE' : 'DISABLED',
                  })
                }
              />
            ),
          },
          {
            title: '需改密',
            dataIndex: 'mustChangePassword',
            render: (must: boolean) => (must ? <Tag color="warning">是</Tag> : <Tag>否</Tag>),
          },
          {
            title: '操作',
            render: (_, record) => (
              <Space>
                <Button
                  type="link"
                  size="small"
                  icon={<EditOutlined />}
                  disabled={record.protectedAccount}
                  onClick={() => openEdit(record)}
                >
                  编辑
                </Button>
                <Button
                  type="link"
                  size="small"
                  icon={<KeyOutlined />}
                  disabled={record.protectedAccount}
                  onClick={() => openReset(record.publicId)}
                >
                  重置密码
                </Button>
              </Space>
            ),
          },
        ]}
      />

      {/* Create User Modal */}
      <Modal
        title="新建系统用户"
        open={createModalOpen}
        onOk={() => createForm.submit()}
        onCancel={() => setCreateModalOpen(false)}
        confirmLoading={createUserMutation.isPending}
        okText="创建用户"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={(vals) => createUserMutation.mutate(vals)}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, message: '请输入用户名' },
              { min: 3, max: 32, message: '用户名长度在3到32字符' },
            ]}
          >
            <Input placeholder="例如: designer_wang" autoComplete="off" />
          </Form.Item>

          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: 6, message: '密码至少6位' },
            ]}
          >
            <Input.Password placeholder="设置初始登录密码" autoComplete="new-password" />
          </Form.Item>

          <Form.Item
            name="displayName"
            label="显示姓名"
            rules={[{ required: true, message: '请输入显示姓名' }]}
          >
            <Input placeholder="例如: 王小明" autoComplete="off" />
          </Form.Item>

          <Form.Item name="department" label="所属部门">
            <Input placeholder="例如: 体验设计中心" />
          </Form.Item>

          <Form.Item
            name="roles"
            label="分配角色"
            rules={[{ required: true, message: '请选择至少一个角色' }]}
          >
            <Select mode="multiple" placeholder="选择角色" options={ROLE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit User Modal */}
      <Modal
        title="编辑用户资料"
        open={editModalOpen}
        onOk={() => editForm.submit()}
        onCancel={() => setEditModalOpen(false)}
        confirmLoading={updateUserMutation.isPending}
        okText="保存修改"
        cancelText="取消"
        destroyOnClose
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(vals) => {
            if (editingUser) {
              updateUserMutation.mutate({ id: editingUser.publicId, values: vals });
            }
          }}
        >
          <Form.Item
            name="displayName"
            label="显示姓名"
            rules={[{ required: true, message: '请输入显示姓名' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="department" label="所属部门">
            <Input />
          </Form.Item>
          <Form.Item
            name="roles"
            label="角色"
            extra="可随时变更为任意角色；其他系统管理员也可以被调整"
            rules={[{ required: true, type: 'array', min: 1, message: '请选择至少一个角色' }]}
          >
            <Select mode="multiple" placeholder="选择角色" options={ROLE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Reset Password Modal */}
      <Modal
        title="重置用户密码"
        open={resetModalOpen}
        onOk={() => resetForm.submit()}
        onCancel={() => setResetModalOpen(false)}
        confirmLoading={resetPasswordMutation.isPending}
        okText="确认重置"
        cancelText="取消"
        destroyOnClose
      >
        <Form
          form={resetForm}
          layout="vertical"
          onFinish={({ newPassword }) => {
            if (resetUserId) {
              resetPasswordMutation.mutate({ id: resetUserId, newPassword });
            }
          }}
        >
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '新密码至少6位' },
            ]}
          >
            <Input.Password placeholder="输入重置后的密码" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};
