import { http } from '@/lib/http';
import axios from 'axios';

export interface CommentReplyItem {
  publicId: string;
  content: string;
  authorType: 'USER' | 'GUEST';
  authorName: string;
  createdAt: string;
  isDeleted: boolean;
}

export interface CommentItem {
  publicId: string;
  prototypePublicId: string;
  versionPublicId: string;
  versionNo: number;
  content: string;
  status: 'OPEN' | 'RESOLVED' | null;
  authorType: 'USER' | 'GUEST';
  authorName: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolveNote?: string;
  rowVersion: number;
  createdAt: string;
  isDeleted: boolean;
  replies: CommentReplyItem[];
}

export interface CreateCommentPayload {
  versionPublicId: string;
  parentCommentPublicId?: string;
  content: string;
}

export interface ResolveCommentPayload {
  status: 'OPEN' | 'RESOLVED';
  resolveNote?: string;
  rowVersion: number;
}

export const commentApi = {
  async list(prototypeId: string, versionId?: string, status?: string): Promise<CommentItem[]> {
    const params: Record<string, string> = {};
    if (versionId) params.versionId = versionId;
    if (status) params.status = status;
    const res = await http.get<{ data: CommentItem[] }>(`/prototypes/${prototypeId}/comments`, { params });
    return res.data.data;
  },

  async create(prototypeId: string, payload: CreateCommentPayload, idempotencyKey?: string): Promise<CommentItem> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    const res = await http.post<{ data: CommentItem }>(`/prototypes/${prototypeId}/comments`, payload, { headers });
    return res.data.data;
  },

  async resolve(prototypeId: string, commentId: string, payload: ResolveCommentPayload): Promise<CommentItem> {
    const res = await http.post<{ data: CommentItem }>(
      `/prototypes/${prototypeId}/comments/${commentId}/resolve`,
      payload
    );
    return res.data.data;
  },

  async delete(prototypeId: string, commentId: string): Promise<void> {
    await http.delete(`/prototypes/${prototypeId}/comments/${commentId}`);
  },
};

const shareClient = axios.create({
  baseURL: '/share-api/v1',
  withCredentials: true,
});

export const publicCommentApi = {
  async list(token: string): Promise<CommentItem[]> {
    const res = await shareClient.get<{ data: CommentItem[] }>(`/shares/${token}/comments`);
    return res.data.data;
  },

  async createGuest(
    token: string,
    payload: CreateCommentPayload,
    csrfToken?: string,
    idempotencyKey?: string
  ): Promise<CommentItem> {
    const headers: Record<string, string> = {};
    if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken;
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
    const res = await shareClient.post<{ data: CommentItem }>(`/shares/${token}/comments`, payload, { headers });
    return res.data.data;
  },
};
