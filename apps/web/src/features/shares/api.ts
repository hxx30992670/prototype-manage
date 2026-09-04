import { http } from '@/lib/http';
import axios from 'axios';

export interface ShareLinkItem {
  shareId: string;
  name: string;
  expiresAt?: string;
  status: 'ACTIVE' | 'DISABLED';
  specScope: 'ALL' | 'CORE_FLOW' | 'NONE';
  allowComment: boolean;
  allowPublicAttachment: boolean;
  hasPassword: boolean;
  authEpoch: number;
  visitCount: number;
  lastVisitedAt?: string;
  createdBy?: string;
  createdAt: string;
}

export interface CreateSharePayload {
  name: string;
  password?: string;
  expiresAt?: string;
  specScope?: string;
  allowComment?: boolean;
  allowPublicAttachment?: boolean;
}

export interface ShareCreatedResponse {
  shareId: string;
  rawUrl?: string;
  secretAvailable: boolean;
}

export interface RotateTokenResponse {
  rawUrl?: string;
  secretAvailable: boolean;
}

export interface BootstrapResponse {
  prototypeName: string;
  prototypePublicId: string;
  requiresPassword: boolean;
  authenticated: boolean;
  csrfToken?: string;
  specScope: string;
  spec?: Record<string, any>;
  attachments?: Array<{
    publicId: string;
    name: string;
    type: string;
    size: number;
    mimeType: string;
  }>;
  allowComment: boolean;
  statusMessage: string;
  currentVersionPublicId?: string;
  currentVersionNo?: number;
}

export interface ContentTicketResponse {
  ticket: string;
  contentUrl: string;
  expiresAt: string;
}

export const shareAdminApi = {
  async list(prototypeId: string): Promise<ShareLinkItem[]> {
    const res = await http.get<{ data: ShareLinkItem[] }>(`/prototypes/${prototypeId}/shares`);
    return res.data.data;
  },

  async create(prototypeId: string, payload: CreateSharePayload, idempotencyKey?: string): Promise<ShareCreatedResponse> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    const res = await http.post<{ data: ShareCreatedResponse }>(
      `/prototypes/${prototypeId}/shares`,
      payload,
      { headers }
    );
    return res.data.data;
  },

  async rotateToken(prototypeId: string, shareId: string): Promise<RotateTokenResponse> {
    const res = await http.post<{ data: RotateTokenResponse }>(
      `/prototypes/${prototypeId}/shares/${shareId}/rotate-token`
    );
    return res.data.data;
  },

  async resetPassword(prototypeId: string, shareId: string, newPassword: string): Promise<void> {
    await http.post(`/prototypes/${prototypeId}/shares/${shareId}/reset-password`, { newPassword });
  },

  async updateStatus(prototypeId: string, shareId: string, status: string): Promise<void> {
    await http.post(`/prototypes/${prototypeId}/shares/${shareId}/status`, { status });
  },
};

// Share-api is unauthenticated or cookie-authenticated with preview domain
const shareClient = axios.create({
  baseURL: '/share-api/v1',
  withCredentials: true,
});

export const sharePublicApi = {
  async bootstrap(token: string): Promise<BootstrapResponse> {
    const res = await shareClient.get<{ data: BootstrapResponse }>(`/shares/${token}/bootstrap`);
    return res.data.data;
  },

  async verifyPassword(token: string, password: string, guestName?: string): Promise<{ success: boolean; csrfToken: string }> {
    const res = await shareClient.post<{ data: { success: boolean; csrfToken: string } }>(
      `/shares/${token}/verify-password`,
      { password, guestName }
    );
    return res.data.data;
  },

  async issueContentTicket(token: string, csrfToken?: string): Promise<ContentTicketResponse> {
    const headers: Record<string, string> = {};
    if (csrfToken) {
      headers['X-CSRF-TOKEN'] = csrfToken;
    }
    const res = await shareClient.post<{ data: ContentTicketResponse }>(
      `/shares/${token}/content-ticket`,
      {},
      { headers }
    );
    return res.data.data;
  },

  async createAttachmentDownloadTicket(
    token: string,
    attachmentId: string,
    csrfToken?: string
  ): Promise<{ url: string; expiresAt: string }> {
    const headers: Record<string, string> = {};
    if (csrfToken) {
      headers['X-CSRF-TOKEN'] = csrfToken;
    }
    const res = await shareClient.post<{ data: { url: string; expiresAt: string } }>(
      `/shares/${token}/attachments/${attachmentId}/download-ticket`,
      {},
      { headers }
    );
    return res.data.data;
  },
};
