import React from 'react';
import { Card, Table, InputNumber, Button, Space, message, Typography, Tag, Popconfirm } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from '@/lib/http';

const { Text } = Typography;

interface ConfigItem {
  key: string;
  value: string;
  defaultValue: string;
  hardMaximum: number | null;
  valueType: 'LONG' | 'BOOLEAN' | 'STRING';
  description: string;
}

export const SystemConfigPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [draft, setDraft] = React.useState<Record<string, string>>({});

  const { data: items = [], isLoading, refetch } = useQuery({
    queryKey: ['admin', 'config'],
    queryFn: async () => {
      const res = await http.get<{ data: ConfigItem[] }>('/admin/config');
      const list = res.data.data;
      setDraft(Object.fromEntries(list.map((i) => [i.key, i.value])));
      return list;
    },
  });

  const updateMutation = useMutation({
    mutationFn: async ({ key, value }: { key: string; value: string }) => {
      await http.put(`/admin/config/${key}`, { value });
    },
    onSuccess: () => {
      message.success('配置已更新');
      queryClient.invalidateQueries({ queryKey: ['admin', 'config'] });
    },
    onError: (err: Error) => {
      message.error(err.message || '更新配置失败');
    },
  });

  const setValue = (key: string, raw: string) => {
    setDraft((prev) => ({ ...prev, [key]: raw }));
  };

  const changed = items.filter((i) => draft[i.key] !== undefined && draft[i.key] !== i.value);

  const renderEditor = (item: ConfigItem) => {
    if (item.valueType === 'BOOLEAN') {
      return (
        <Button
          size="small"
          onClick={() => setValue(item.key, draft[item.key] === 'true' ? 'false' : 'true')}
        >
          {draft[item.key] === 'true' ? '已开启' : '已关闭'}
        </Button>
      );
    }
    return (
      <InputNumber
        size="small"
        style={{ width: 160 }}
        min={1}
        max={item.hardMaximum ?? undefined}
        value={draft[item.key] !== undefined ? Number(draft[item.key]) : Number(item.value)}
        onChange={(v) => setValue(item.key, String(v ?? 0))}
        disabled={item.hardMaximum === null}
      />
    );
  };

  const columns = [
    {
      title: '配置项',
      dataIndex: 'key',
      width: 260,
      render: (_: string, item: ConfigItem) => (
        <Space direction="vertical" size={0}>
          <Text code>{item.key}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{item.description}</Text>
        </Space>
      ),
    },
    {
      title: '当前值',
      key: 'value',
      width: 200,
      render: (_: unknown, item: ConfigItem) => renderEditor(item),
    },
    {
      title: '默认值',
      dataIndex: 'defaultValue',
      width: 100,
    },
    {
      title: '硬上限',
      key: 'hardMaximum',
      width: 100,
      render: (_: unknown, item: ConfigItem) => item.hardMaximum ?? '—',
    },
    {
      title: '类型',
      dataIndex: 'valueType',
      width: 90,
      render: (t: string) => <Tag>{t}</Tag>,
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (_: unknown, item: ConfigItem) =>
        draft[item.key] !== undefined && draft[item.key] !== item.value ? (
          <Tag color="orange">已修改</Tag>
        ) : (
          <Tag>未修改</Tag>
        ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, item: ConfigItem) => (
        <Popconfirm
          title="确认更新该配置？"
          okText="确定"
          cancelText="取消"
          onConfirm={() => updateMutation.mutate({ key: item.key, value: draft[item.key] ?? item.value })}
        >
          <Button
            type="link"
            size="small"
            disabled={draft[item.key] === undefined || draft[item.key] === item.value}
            loading={updateMutation.isPending}
          >
            保存
          </Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <Card
      title="系统配置"
      extra={
        <Space>
          <Text type="secondary">共 {items.length} 项，已修改 {changed.length} 项</Text>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
            刷新
          </Button>
        </Space>
      }
    >
      <Table<ConfigItem>
        rowKey="key"
        loading={isLoading}
        dataSource={items}
        locale={{ emptyText: '暂无配置项' }}
        columns={columns}
        pagination={false}
        size="middle"
      />
    </Card>
  );
};
