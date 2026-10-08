import React, { useState } from "react";
import {
  Table,
  Button,
  Input,
  Select,
  Space,
  Tag,
  Card,
  Popconfirm,
  message,
  Dropdown,
  Modal,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  InboxOutlined,
  CheckCircleOutlined,
  DownOutlined,
} from "@ant-design/icons";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import {
  prototypeApi,
  PrototypeItem,
  PrototypeQuery,
  prototypeKeys,
  isPrototypeOwner,
  ownerNames,
  visibilityLabel,
} from "./api";
import { getReviewStatusTag } from "./status";
import { PrototypeForm } from "./PrototypeForm";
import { authApi } from "@/features/auth/api";

function versionStatusLabel(record: PrototypeItem): string {
  if (!record.currentVersionStatus) return "尚未发布版本";
  if (record.currentVersionStatus === "PUBLISHED") {
    return record.currentVersionNo != null
      ? `已发布 v${record.currentVersionNo}`
      : "已发布";
  }
  if (record.currentVersionStatus === "PUBLISHING") return "发布中";
  if (record.currentVersionStatus === "FAILED") return "发布失败";
  return record.currentVersionStatus;
}

export const PrototypeListPage: React.FC<{
  initialQuery?: Partial<PrototypeQuery>;
  title?: string;
}> = ({ initialQuery, title = "原型资产库" }) => {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [editingPrototype, setEditingPrototype] =
    useState<PrototypeItem | null>(null);

  const [query, setQuery] = useState<PrototypeQuery>({
    page: 1,
    pageSize: 20,
    ...initialQuery,
  });

  const { data: currentUser } = useQuery({
    queryKey: ["auth", "me"],
    queryFn: () => authApi.getCurrentUser(),
  });

  const isCreatorOrAdmin = currentUser?.roles.some(
    (r) =>
      r === "CREATOR" ||
      r === "ROLE_CREATOR" ||
      r === "ADMIN" ||
      r === "ROLE_ADMIN",
  );

  const { data: categories = [] } = useQuery({
    queryKey: ["catalog", "categories"],
    queryFn: () => prototypeApi.listCategories(),
  });

  const { data: pagedData, isLoading } = useQuery({
    queryKey: prototypeKeys.list(query),
    queryFn: () => prototypeApi.list(query),
  });

  const archiveMutation = useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      prototypeApi.archive(id, archived),
    onSuccess: (_, variables) => {
      message.success(variables.archived ? "原型已成功归档" : "原型已取消归档");
      queryClient.invalidateQueries({ queryKey: prototypeKeys.all });
    },
    onError: (err: Error) => {
      message.error(err.message || "归档操作失败");
    },
  });

  const reviewStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      prototypeApi.updateReviewStatus(id, status),
    onSuccess: () => {
      message.success("评审状态更新成功");
      queryClient.invalidateQueries({ queryKey: prototypeKeys.all });
    },
    onError: (err: Error) => {
      message.error(err.message || "更新评审状态失败");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => prototypeApi.delete(id),
    onSuccess: () => {
      message.success("已移至回收站");
      queryClient.invalidateQueries({ queryKey: prototypeKeys.all });
    },
    onError: (err: Error) => {
      message.error(err.message || "删除失败");
    },
  });

  const canManage = (record: PrototypeItem) => {
    if (!currentUser) return false;
    if (currentUser.roles.some((r) => r === "ADMIN" || r === "ROLE_ADMIN"))
      return true;
    return (
      record.createdBy?.publicId === currentUser.publicId ||
      isPrototypeOwner(record, currentUser.publicId)
    );
  };
  const showMessage = (record: PrototypeItem) => {
    Modal.confirm({
      title: record.archived ? "取消归档确认" : "归档确认",
      content: record.archived
        ? "取消归档后原型将恢复正常编辑与发布流程。"
        : "归档后原型将冻结所有修改、新版本发布和评论。确认归档吗？",
      okText: "确定",
      cancelText: "取消",
      onOk: () =>
        archiveMutation.mutate({
          id: record.publicId,
          archived: !record.archived,
        }),
    });
  };

  const columns: ColumnsType<PrototypeItem> = [
    {
      title: "原型名称 / 编码",
      key: "name",
      render: (_, record) => (
        <div>
          <Link
            to={`/prototypes/${record.publicId}`}
            className="text-[15px] font-semibold text-signal hover:text-gold"
          >
            {record.name}
          </Link>
          <div className="font-mono text-xs text-mute">{record.code}</div>
          {record.archived && (
            <Tag color="warning" style={{ marginTop: 4 }}>
              已归档
            </Tag>
          )}
        </div>
      ),
    },
    {
      title: "分类",
      key: "category",
      dataIndex: ["category", "name"],
      render: (text) => text || "-",
    },
    {
      title: "评审状态",
      key: "reviewStatus",
      render: (_, record) => getReviewStatusTag(record.reviewStatus),
    },
    {
      title: "版本状态",
      key: "versionStatus",
      render: (_, record) => (
        <span
          className={
            record.currentVersionStatus === "PUBLISHED"
              ? "text-signal"
              : "text-mute"
          }
        >
          {versionStatusLabel(record)}
        </span>
      ),
    },
    {
      title: "负责人",
      key: "owner",
      render: (_, record) => ownerNames(record),
    },
    {
      title: "可见范围",
      key: "visibility",
      render: (_, record) => visibilityLabel(record.visibility),
    },
    {
      title: "更新时间",
      key: "updatedAt",
      dataIndex: "updatedAt",
      render: (text: string) => (text ? new Date(text).toLocaleString() : "-"),
    },
    {
      title: "操作",
      key: "actions",
      render: (_, record) => {
        const allowed = canManage(record);
        if (!allowed) {
          return (
            <Link to={`/prototypes/${record.publicId}`}>
              <Button type="link" size="small">
                查看详情
              </Button>
            </Link>
          );
        }

        const reviewMenuItems = [
          {
            key: "DRAFT",
            label: "设为草稿",
            onClick: () =>
              reviewStatusMutation.mutate({
                id: record.publicId,
                status: "DRAFT",
              }),
          },
          {
            key: "PENDING_REVIEW",
            label: "发起评审",
            onClick: () =>
              reviewStatusMutation.mutate({
                id: record.publicId,
                status: "PENDING_REVIEW",
              }),
          },
          {
            key: "APPROVED",
            label: "审核通过",
            onClick: () =>
              reviewStatusMutation.mutate({
                id: record.publicId,
                status: "APPROVED",
              }),
          },
          {
            key: "REJECTED",
            label: "审核驳回",
            onClick: () =>
              reviewStatusMutation.mutate({
                id: record.publicId,
                status: "REJECTED",
              }),
          },
        ];

        return (
          <Space size="small">
            <Link to={`/prototypes/${record.publicId}`}>
              <Button type="link" size="small">
                详情
              </Button>
            </Link>
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              disabled={record.archived}
              onClick={() => {
                setEditingPrototype(record);
                setFormOpen(true);
              }}
            >
              编辑
            </Button>

            <Dropdown
              menu={{ items: reviewMenuItems }}
              disabled={record.archived}
            >
              <Button type="link" size="small" icon={<CheckCircleOutlined />}>
                评审状态 <DownOutlined />
              </Button>
            </Dropdown>

            <Button
              type="link"
              size="small"
              icon={<InboxOutlined />}
              onClick={() => {
                showMessage(record);
              }}
            >
              {record.archived ? "取消归档" : "归档"}
            </Button>

            <Popconfirm
              title="确定移至回收站吗？"
              description="删除后可在回收站恢复或彻底清除"
              okText="确定"
              cancelText="取消"
              onConfirm={() => deleteMutation.mutate(record.publicId)}
            >
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title={title}
      extra={
        isCreatorOrAdmin && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditingPrototype(null);
              setFormOpen(true);
            }}
          >
            新建原型
          </Button>
        )
      }
    >
      <div className="mb-4 flex flex-wrap gap-3">
        <Input.Search
          placeholder="搜索原型名称、编码或描述"
          allowClear
          className="w-[260px]"
          onSearch={(val) =>
            setQuery((prev) => ({ ...prev, keyword: val, page: 1 }))
          }
        />

        <Select
          placeholder="全部分类"
          allowClear
          className="w-40"
          value={query.categoryId}
          onChange={(val) =>
            setQuery((prev) => ({ ...prev, categoryId: val, page: 1 }))
          }
          options={categories.map((c) => ({ label: c.name, value: c.code }))}
        />

        <div className="inline-flex items-center">
          <label
            htmlFor="reviewStatusSelect"
            className="mr-2 text-sm text-mute"
          >
            评审状态
          </label>
          <select
            id="reviewStatusSelect"
            aria-label="评审状态"
            value={query.reviewStatus || ""}
            className="h-8 rounded-md border border-signal/20 bg-panel px-2 text-sm text-ink"
            onChange={(e) => {
              const val = e.target.value;
              setQuery((prev) => ({
                ...prev,
                reviewStatus: val || undefined,
                page: 1,
              }));
            }}
          >
            <option value="">全部评审状态</option>
            <option value="DRAFT">草稿</option>
            <option value="PENDING_REVIEW">评审中</option>
            <option value="APPROVED">审核通过</option>
            <option value="REJECTED">审核驳回</option>
          </select>
        </div>

        <Select
          placeholder="归档状态"
          allowClear
          className="w-[130px]"
          value={
            query.archived !== undefined ? String(query.archived) : undefined
          }
          onChange={(val) =>
            setQuery((prev) => ({
              ...prev,
              archived: val !== undefined ? val === "true" : undefined,
              page: 1,
            }))
          }
          options={[
            { label: "未归档", value: "false" },
            { label: "已归档", value: "true" },
          ]}
        />
      </div>

      <Table
        rowKey="publicId"
        columns={columns}
        dataSource={pagedData?.data || []}
        loading={isLoading}
        locale={{ emptyText: "暂无原型资产" }}
        pagination={{
          current: query.page || 1,
          pageSize: query.pageSize || 20,
          total: pagedData?.pagination.total || 0,
          showSizeChanger: true,
          pageSizeOptions: ["10", "20", "50", "100"],
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, pageSize) =>
            setQuery((prev) => ({ ...prev, page, pageSize })),
        }}
      />

      <PrototypeForm
        open={formOpen}
        prototype={editingPrototype}
        onClose={() => setFormOpen(false)}
      />
    </Card>
  );
};
