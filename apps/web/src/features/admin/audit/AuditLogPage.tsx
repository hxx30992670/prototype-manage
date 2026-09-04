import React from 'react';
import { Card, Table, Input, Select, DatePicker, Space, Button, Tag, message, Typography } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { http } from '@/lib/http';

const { RangePicker } = DatePicker;
const { Text } = Typography;

const formatTime = (iso: string): string => {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

interface AuditRecord {
  id: number;
  traceId: string;
  actorType: string;
  actorId: number | null;
  action: string;
  targetType: string;
  targetId: string;
  result: string;
  ip?: string;
  userAgent?: string;
  summary?: string;
  createdAt: string;
}

const ACTION_OPTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILURE',
  'USER_CREATE', 'USER_UPDATE', 'USER_UPDATE_ROLES', 'USER_UPDATE_STATUS', 'USER_RESET_PASSWORD',
  'PROTOTYPE_CREATE', 'PROTOTYPE_UPDATE', 'PROTOTYPE_DELETE', 'PROTOTYPE_RESTORE', 'PROTOTYPE_PURGE',
  'PROTOTYPE_ARCHIVE', 'PROTOTYPE_UNARCHIVE',
  'VERSION_UPLOAD', 'VERSION_PUBLISH_SUCCESS', 'VERSION_PUBLISH_FAILURE', 'VERSION_ROLLBACK', 'VERSION_DELETE',
  'SPEC_UPDATE', 'ATTACHMENT_UPLOAD', 'ATTACHMENT_DELETE',
  'SHARE_CREATE', 'SHARE_UPDATE', 'SHARE_RESET_PASSWORD', 'SHARE_ROTATE_TOKEN', 'SHARE_STATUS_UPDATE',
  'COMMENT_RESOLVE', 'COMMENT_REOPEN', 'COMMENT_DELETE',
  'SYSTEM_CONFIG_UPDATE', 'TAG_MERGE',
].sort();

export const AuditLogPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [query, setQuery] = React.useState<{
    action?: string;
    result?: string;
    targetId?: string;
    traceId?: string;
    from?: string;
    to?: string;
    page: number;
    pageSize: number;
  }>({ page: 1, pageSize: 20 });

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['admin', 'audit', query],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (query.action) params.set('action', query.action);
      if (query.result) params.set('result', query.result);
      if (query.targetId) params.set('targetId', query.targetId);
      if (query.traceId) params.set('traceId', query.traceId);
      if (query.from) params.set('from', query.from);
      if (query.to) params.set('to', query.to);
      params.set('page', String(query.page));
      params.set('pageSize', String(query.pageSize));
      const res = await http.get<{ data: AuditRecord[]; pagination: { page: number; pageSize: number; total: number; totalPages: number } }>(
        `/admin/audit-logs?${params.toString()}`,
      );
      return res.data;
    },
  });

  const columns = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (v: string) => formatTime(v),
    },
    {
      title: '操作',
      dataIndex: 'action',
      width: 200,
      render: (v: string) => <Text code>{v}</Text>,
    },
    {
      title: '对象',
      key: 'target',
      width: 220,
      render: (_: unknown, r: AuditRecord) => (
        <Space size={4} wrap>
          <Tag>{r.targetType}</Tag>
          <Text style={{ fontSize: 12 }}>{r.targetId}</Text>
        </Space>
      ),
    },
    {
      title: '结果',
      dataIndex: 'result',
      width: 90,
      render: (v: string) =>
        v === 'SUCCESS' ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag>,
    },
    {
      title: '操作者',
      key: 'actor',
      width: 80,
      render: (_: unknown, r: AuditRecord) => r.actorId ?? '—',
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      width: 130,
    },
    {
      title: '摘要',
      dataIndex: 'summary',
      ellipsis: true,
    },
    {
      title: '追踪 ID',
      dataIndex: 'traceId',
      width: 200,
      render: (v: string) => <Text style={{ fontSize: 12 }} copyable={{ text: v }}>{v.slice(0, 16)}…</Text>,
    },
  ];

  return (
    <Card
      title="审计日志"
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
          刷新
        </Button>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          showSearch
          placeholder="操作类型"
          style={{ width: 220 }}
          options={ACTION_OPTIONS.map((a) => ({ value: a, label: a }))}
          onChange={(v) => setQuery((q) => ({ ...q, action: v ?? undefined, page: 1 }))}
        />
        <Select
          allowClear
          placeholder="结果"
          style={{ width: 100 }}
          options={[
            { value: 'SUCCESS', label: '成功' },
            { value: 'FAILURE', label: '失败' },
          ]}
          onChange={(v) => setQuery((q) => ({ ...q, result: v ?? undefined, page: 1 }))}
        />
        <Input
          placeholder="对象 ID（如原型 public_id）"
          style={{ width: 220 }}
          allowClear
          onChange={(e) => setQuery((q) => ({ ...q, targetId: e.target.value || undefined, page: 1 }))}
        />
        <Input
          placeholder="追踪 ID"
          style={{ width: 200 }}
          allowClear
          onChange={(e) => setQuery((q) => ({ ...q, traceId: e.target.value || undefined, page: 1 }))}
        />
        <RangePicker
          showTime
          onChange={(dates) =>
            setQuery((q) => ({
              ...q,
              from: dates?.[0] ? dates[0].toISOString() : undefined,
              to: dates?.[1] ? dates[1].toISOString() : undefined,
              page: 1,
            }))
          }
        />
        <Button
          type="primary"
          icon={<SearchOutlined />}
          onClick={() => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'audit'] });
            refetch();
            message.success('查询完成');
          }}
        >
          查询
        </Button>
      </Space>
      <Table<AuditRecord>
        rowKey="id"
        loading={isLoading}
        dataSource={data?.data ?? []}
        columns={columns}
        locale={{ emptyText: '暂无审计日志' }}
        pagination={{
          current: query.page,
          pageSize: query.pageSize,
          total: data?.pagination.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, pageSize) => setQuery((q) => ({ ...q, page, pageSize })),
        }}
        size="middle"
      />
    </Card>
  );
};
