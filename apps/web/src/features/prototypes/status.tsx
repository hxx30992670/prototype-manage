import { Tag } from 'antd';

export const REVIEW_STATUS_MAP: Record<string, { label: string; color?: string }> = {
  DRAFT: { label: '草稿' },
  PENDING_REVIEW: { label: '评审中', color: 'processing' },
  APPROVED: { label: '已通过', color: 'success' },
  REJECTED: { label: '已驳回', color: 'error' },
};

export function getReviewStatusLabel(status?: string): string {
  if (!status) return '-';
  return REVIEW_STATUS_MAP[status]?.label || status;
}

export function getReviewStatusTag(status?: string) {
  if (!status) return <Tag>-</Tag>;
  const info = REVIEW_STATUS_MAP[status];
  if (!info) return <Tag>{status}</Tag>;
  return <Tag color={info.color}>{info.label}</Tag>;
}
