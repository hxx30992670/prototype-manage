import React, { useState, useEffect } from 'react';
import { Card, Table, Tag, Button, Space } from 'antd';
import { useNavigate } from 'react-router-dom';
import { http } from '@/lib/http';

interface ReviewActivityItem {
  id: string;
  prototypeId: string;
  prototypeName: string;
  versionNo: number;
  content: string;
  status: 'OPEN' | 'RESOLVED';
  authorName: string;
  authorType: 'USER' | 'GUEST';
  createdAt: string;
}

export const ReviewActivityPage: React.FC = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [activities, setActivities] = useState<ReviewActivityItem[]>([]);

  const fetchActivities = async () => {
    try {
      setLoading(true);
      // Fetch prototypes to get review comments overview
      const protoRes = await http.get<{ data: { items: any[] } }>('/prototypes', {
        params: { page: 1, pageSize: 20 },
      });
      const items = protoRes.data?.data?.items || [];

      // Collect comments across top prototypes
      const collected: ReviewActivityItem[] = [];
      for (const p of items.slice(0, 10)) {
        try {
          const comRes = await http.get<{ data: any[] }>(`/prototypes/${p.publicId}/comments`);
          const comments = comRes.data?.data || [];
          for (const c of comments) {
            collected.push({
              id: c.publicId,
              prototypeId: p.publicId,
              prototypeName: p.name,
              versionNo: c.versionNo,
              content: c.content,
              status: c.status,
              authorName: c.authorName,
              authorType: c.authorType,
              createdAt: c.createdAt,
            });
          }
        } catch {
          // ignore error on prototypes without permissions
        }
      }
      setActivities(collected);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchActivities();
  }, []);

  const columns = [
    {
      title: '所属原型',
      dataIndex: 'prototypeName',
      key: 'prototypeName',
      render: (name: string) => (
        <Button
          type="link"
          className="p-0 font-medium"
          onClick={() => navigate(`/prototypes`)}
        >
          {name}
        </Button>
      ),
    },
    {
      title: '针对版本',
      dataIndex: 'versionNo',
      key: 'versionNo',
      render: (ver: number) => <Tag color="blue">v{ver}</Tag>,
    },
    {
      title: '评审内容',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
    },
    {
      title: '提出人',
      dataIndex: 'authorName',
      key: 'authorName',
      render: (name: string, record: ReviewActivityItem) => (
        <Space>
          <span>{name}</span>
          <Tag color={record.authorType === 'GUEST' ? 'orange' : 'cyan'}>
            {record.authorType === 'GUEST' ? '访客' : '成员'}
          </Tag>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'RESOLVED' ? 'green' : 'gold'}>
          {status === 'RESOLVED' ? '已解决' : '待处理'}
        </Tag>
      ),
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (t: string) => new Date(t).toLocaleString(),
    },
  ];

  return (
    <Card
      title="评审动态与协作工作台"
      extra={
        <Button onClick={fetchActivities} loading={loading}>
          刷新
        </Button>
      }
    >
      <Table
        rowKey="id"
        dataSource={activities}
        columns={columns}
        loading={loading}
        locale={{ emptyText: '暂无评审动态' }}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (total) => `共 ${total} 条`,
        }}
      />
    </Card>
  );
};
