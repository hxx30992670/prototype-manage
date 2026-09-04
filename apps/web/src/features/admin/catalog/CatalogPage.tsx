import React, { useState } from 'react';
import { Card, Tabs, Table, Button, Modal, Form, Input, InputNumber, Popconfirm, message, Space, Tag, Select } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, MergeCellsOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { prototypeApi, CategoryItem, TagItem } from '@/features/prototypes/api';
import { http } from '@/lib/http';

export const CatalogPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState('categories');

  // Category Modal
  const [catModalOpen, setCatModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryItem | null>(null);
  const [catForm] = Form.useForm();

  // Tag Modal
  const [tagModalOpen, setTagModalOpen] = useState(false);
  const [editingTag, setEditingTag] = useState<TagItem | null>(null);
  const [tagForm] = Form.useForm();

  const { data: categories = [], isLoading: catLoading } = useQuery({
    queryKey: ['catalog', 'categories'],
    queryFn: () => prototypeApi.listCategories(),
  });

  const { data: tags = [], isLoading: tagLoading } = useQuery({
    queryKey: ['catalog', 'tags'],
    queryFn: () => prototypeApi.listTags(),
  });

  const saveCategoryMutation = useMutation({
    mutationFn: async (values: CategoryItem) => {
      if (editingCategory) {
        await http.put(`/admin/categories/${editingCategory.code}`, values);
      } else {
        await http.post('/admin/categories', values);
      }
    },
    onSuccess: () => {
      message.success(editingCategory ? '分类更新成功' : '分类创建成功');
      queryClient.invalidateQueries({ queryKey: ['catalog', 'categories'] });
      setCatModalOpen(false);
    },
    onError: (err: Error) => {
      message.error(err.message || '操作分类失败');
    },
  });

  const deleteCategoryMutation = useMutation({
    mutationFn: async (code: string) => {
      await http.delete(`/admin/categories/${code}`);
    },
    onSuccess: () => {
      message.success('分类已删除');
      queryClient.invalidateQueries({ queryKey: ['catalog', 'categories'] });
    },
    onError: (err: Error) => {
      message.error(err.message || '删除分类失败');
    },
  });

  const saveTagMutation = useMutation({
    mutationFn: async (values: TagItem) => {
      if (editingTag) {
        await http.put(`/admin/tags/${editingTag.name}`, values);
      } else {
        await http.post('/tags', values);
      }
    },
    onSuccess: () => {
      message.success(editingTag ? '标签更新成功' : '标签创建成功');
      queryClient.invalidateQueries({ queryKey: ['catalog', 'tags'] });
      setTagModalOpen(false);
    },
    onError: (err: Error) => {
      message.error(err.message || '操作标签失败');
    },
  });

  const deleteTagMutation = useMutation({
    mutationFn: async (name: string) => {
      await http.delete(`/admin/tags/${name}`);
    },
    onSuccess: () => {
      message.success('标签已删除');
      queryClient.invalidateQueries({ queryKey: ['catalog', 'tags'] });
    },
    onError: (err: Error) => {
      message.error(err.message || '删除标签失败');
    },
  });

  const [mergeSource, setMergeSource] = useState<TagItem | null>(null);
  const [mergeTarget, setMergeTarget] = useState<string>();

  const mergeTagMutation = useMutation({
    mutationFn: async ({ source, target }: { source: string; target: string }) => {
      const res = await http.post<{ data: { affectedPrototypeCount: number } }>(
        `/admin/tags/${encodeURIComponent(source)}/merge`,
        { targetTagName: target },
      );
      return res.data.data;
    },
    onSuccess: (data) => {
      message.success(`合并成功，影响 ${data.affectedPrototypeCount} 个原型`);
      queryClient.invalidateQueries({ queryKey: ['catalog', 'tags'] });
      setMergeSource(null);
      setMergeTarget(undefined);
    },
    onError: (err: Error) => {
      message.error(err.message || '合并标签失败');
    },
  });

  const openCatModal = (cat?: CategoryItem) => {
    setEditingCategory(cat || null);
    if (cat) {
      catForm.setFieldsValue(cat);
    } else {
      catForm.resetFields();
      catForm.setFieldsValue({ sortNo: 0 });
    }
    setCatModalOpen(true);
  };

  const openTagModal = (tag?: TagItem) => {
    setEditingTag(tag || null);
    if (tag) {
      tagForm.setFieldsValue(tag);
    } else {
      tagForm.resetFields();
      tagForm.setFieldsValue({ color: '#1677ff' });
    }
    setTagModalOpen(true);
  };

  return (
    <Card title="分类与标签管理">
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'categories',
            label: '分类管理',
            children: (
              <div>
                <div style={{ marginBottom: 16 }}>
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => openCatModal()}>
                    新增分类
                  </Button>
                </div>
                <Table
                  rowKey="code"
                  loading={catLoading}
                  dataSource={categories}
                  columns={[
                    { title: '分类名称', dataIndex: 'name' },
                    { title: '分类编码', dataIndex: 'code' },
                    { title: '排序值', dataIndex: 'sortNo' },
                    {
                      title: '操作',
                      render: (_, record) => (
                        <Space>
                          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openCatModal(record)}>
                            编辑
                          </Button>
                          <Popconfirm title="确定删除该分类吗？" okText="确定" cancelText="取消" onConfirm={() => deleteCategoryMutation.mutate(record.code)}>
                            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                              删除
                            </Button>
                          </Popconfirm>
                        </Space>
                      ),
                    },
                  ]}
                  locale={{ emptyText: '暂无分类' }}
                  pagination={false}
                />
              </div>
            ),
          },
          {
            key: 'tags',
            label: '标签管理',
            children: (
              <div>
                <div style={{ marginBottom: 16 }}>
                  <Button type="primary" icon={<PlusOutlined />} onClick={() => openTagModal()}>
                    新增标签
                  </Button>
                </div>
                <Table
                  rowKey="name"
                  loading={tagLoading}
                  dataSource={tags}
                  locale={{ emptyText: '暂无标签' }}
                  columns={[
                    {
                      title: '标签名称',
                      dataIndex: 'name',
                      render: (name, record) => <Tag color={record.color || 'blue'}>{name}</Tag>,
                    },
                    { title: '颜色值', dataIndex: 'color' },
                    {
                      title: '操作',
                      render: (_, record) => (
                        <Space>
                          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openTagModal(record)}>
                            编辑
                          </Button>
                          <Button
                            type="link"
                            size="small"
                            icon={<MergeCellsOutlined />}
                            onClick={() => {
                              setMergeSource(record);
                              setMergeTarget(undefined);
                            }}
                            disabled={tags.length < 2}
                          >
                            合并
                          </Button>
                          <Popconfirm title="确定删除该标签吗？" okText="确定" cancelText="取消" onConfirm={() => deleteTagMutation.mutate(record.name)}>
                            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                              删除
                            </Button>
                          </Popconfirm>
                        </Space>
                      ),
                    },
                  ]}
                  pagination={false}
                />
              </div>
            ),
          },
        ]}
      />

      {/* Category Modal */}
      <Modal
        title={editingCategory ? '编辑分类' : '新建分类'}
        open={catModalOpen}
        onOk={() => catForm.submit()}
        onCancel={() => setCatModalOpen(false)}
        confirmLoading={saveCategoryMutation.isPending}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={catForm} layout="vertical" onFinish={(vals) => saveCategoryMutation.mutate(vals)}>
          <Form.Item
            name="code"
            label="分类编码"
            rules={[{ required: true, message: '请输入分类编码' }]}
          >
            <Input disabled={!!editingCategory} placeholder="例如: finance" />
          </Form.Item>
          <Form.Item
            name="name"
            label="分类名称"
            rules={[{ required: true, message: '请输入分类名称' }]}
          >
            <Input placeholder="例如: 金融业务" />
          </Form.Item>
          <Form.Item name="sortNo" label="排序值">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Tag Modal */}
      <Modal
        title={editingTag ? '编辑标签' : '新建标签'}
        open={tagModalOpen}
        onOk={() => tagForm.submit()}
        onCancel={() => setTagModalOpen(false)}
        confirmLoading={saveTagMutation.isPending}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form form={tagForm} layout="vertical" onFinish={(vals) => saveTagMutation.mutate(vals)}>
          <Form.Item
            name="name"
            label="标签名称"
            rules={[{ required: true, message: '请输入标签名称' }]}
          >
            <Input disabled={!!editingTag} placeholder="例如: React前端" />
          </Form.Item>
          <Form.Item name="color" label="颜色代码">
            <Input placeholder="例如: #1677ff" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Merge Tag Modal */}
      <Modal
        title={`合并标签「${mergeSource?.name ?? ''}」到目标标签`}
        open={!!mergeSource}
        onOk={() => {
          if (mergeSource && mergeTarget) {
            mergeTagMutation.mutate({ source: mergeSource.name, target: mergeTarget });
          }
        }}
        onCancel={() => {
          setMergeSource(null);
          setMergeTarget(undefined);
        }}
        confirmLoading={mergeTagMutation.isPending}
        okText="确认合并"
        cancelText="取消"
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            合并后「{mergeSource?.name}」将被删除，其引用的原型全部改挂到目标标签。
          </div>
          <Select
            style={{ width: '100%' }}
            placeholder="选择目标标签"
            value={mergeTarget}
            onChange={setMergeTarget}
            options={tags
              .filter((t) => t.name !== mergeSource?.name)
              .map((t) => ({ value: t.name, label: t.name }))}
          />
        </Space>
      </Modal>
    </Card>
  );
};
