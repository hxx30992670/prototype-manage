import React from 'react';
import { Card, Table, Button, Popconfirm, message, Typography } from 'antd';
import { RollbackOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { http } from '@/lib/http';
import { getReviewStatusTag } from '@/features/prototypes/status';

const { Text } = Typography;

interface RecycleRecord {
  publicId: string;
  code: string;
  name: string;
  reviewStatus: string;
  deletedAt: string;
  createdAt: string;
}

const formatTime = (iso: string): string => {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

export const RecycleBinPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = React.useState(1);

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['recycle-bin', page],
    queryFn: async () => {
      const res = await http.get<{
        data: RecycleRecord[];
        pagination: { page: number; pageSize: number; total: number; totalPages: number };
      }>(`/admin/recycle-bin?page=${page}&pageSize=20`);
      return res.data;
    },
  });

  const restoreMutation = useMutation({
    mutationFn: async (publicId: string) => {
      await http.post(`/admin/prototypes/${publicId}/restore`);
    },
    onSuccess: () => {
      message.success('原型已恢复（分享链接保持停用）');
      queryClient.invalidateQueries({ queryKey: ['recycle-bin'] });
    },
    onError: (err: Error) => {
      message.error(err.message || '恢复失败');
    },
  });

  const purgeMutation = useMutation({
    mutationFn: async (publicId: string) => {
      await http.delete(`/admin/prototypes/${publicId}/purge`);
    },
    onSuccess: () => {
      message.success('已创建彻底删除任务，对象将被异步清理');
      queryClient.invalidateQueries({ queryKey: ['recycle-bin'] });
    },
    onError: (err: Error) => {
      message.error(err.message || '彻底删除失败');
    },
  });

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string, r: RecycleRecord) => (
        <div>
          <Text strong>{name}</Text>
          <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>{r.code}</Text>
        </div>
      ),
    },
    {
      title: '评审状态',
      dataIndex: 'reviewStatus',
      width: 110,
      render: (v: string) => getReviewStatusTag(v),
    },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      width: 170,
      render: (v: string) => formatTime(v),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v: string) => formatTime(v),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_: unknown, r: RecycleRecord) => (
        <span>
          <Popconfirm
            title="恢复该原型？分享链接不会自动启用。"
            okText="确定"
            cancelText="取消"
            onConfirm={() => restoreMutation.mutate(r.publicId)}
          >
            <Button type="link" size="small" icon={<RollbackOutlined />}>
              恢复
            </Button>
          </Popconfirm>
          <Popconfirm
            title="彻底删除？原型、版本、附件与评论将不可恢复！"
            okText="确定"
            cancelText="取消"
            onConfirm={() => purgeMutation.mutate(r.publicId)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              彻底删除
            </Button>
          </Popconfirm>
        </span>
      ),
    },
  ];

  return (
    <Card
      title="回收站"
      extra={
        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
          刷新
        </Button>
      }
    >
      <Table<RecycleRecord>
        rowKey="publicId"
        loading={isLoading}
        dataSource={data?.data ?? []}
        columns={columns}
        pagination={{
          current: page,
          pageSize: 20,
          total: data?.pagination.total ?? 0,
          showTotal: (total) => `共 ${total} 条`,
          onChange: setPage,
        }}
        locale={{ emptyText: '回收站暂无原型' }}
      />
    </Card>
  );
};
