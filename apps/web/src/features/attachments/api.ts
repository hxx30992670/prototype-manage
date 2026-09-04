import axios from 'axios';
import { http } from '@/lib/http';

export interface AttachmentItem {
  publicId: string;
  prototypePublicId: string;
  versionPublicId?: string;
  versionNo?: number;
  name: string;
  type: 'IMAGE' | 'DOCUMENT' | 'ICON' | 'OTHER';
  purpose?: string;
  accessScope: 'INTERNAL' | 'PUBLIC';
  size: number;
  mimeType: string;
  createdBy?: string;
  createdAt: string;
}

export interface CreateAttachmentPayload {
  uploadId: string;
  name: string;
  type: string;
  purpose?: string;
  accessScope: string;
  versionPublicId?: string;
}

export interface DownloadTicketResponse {
  url: string;
  expiresAt: string;
}

export const attachmentApi = {
  async createUpload(
    filename: string,
    claimedSize: number,
    fileType: 'ATTACHMENT' | 'COVER'
  ): Promise<{ uploadId: string; uploadUrl: string; expiresAt: string }> {
    const res = await http.post<{ data: { uploadId: string; uploadUrl: string; expiresAt: string } }>('/uploads', {
      filename,
      claimedSize,
      fileType,
    });
    return res.data.data;
  },

  async directUpload(presignedUrl: string, file: File): Promise<void> {
    await axios.put(presignedUrl, file, {
      headers: {
        'Content-Type': file.type || 'application/octet-stream',
      },
    });
  },

  async completeUpload(uploadId: string): Promise<void> {
    await http.post(`/uploads/${uploadId}/complete`, { checksum: '' });
  },

  async listForPrototype(prototypeId: string): Promise<AttachmentItem[]> {
    const res = await http.get<{ data: AttachmentItem[] }>(`/prototypes/${prototypeId}/attachments`);
    return res.data.data;
  },

  async listGlobal(params?: { prototypeId?: string; type?: string; createdBy?: string }): Promise<AttachmentItem[]> {
    const res = await http.get<{ data: AttachmentItem[] }>('/attachments', { params });
    return res.data.data;
  },

  async create(prototypeId: string, payload: CreateAttachmentPayload): Promise<AttachmentItem> {
    const res = await http.post<{ data: AttachmentItem }>(`/prototypes/${prototypeId}/attachments`, payload);
    return res.data.data;
  },

  async delete(attachmentId: string): Promise<void> {
    await http.delete(`/attachments/${attachmentId}`);
  },

  async getDownloadTicket(attachmentId: string): Promise<DownloadTicketResponse> {
    const res = await http.get<{ data: DownloadTicketResponse }>(`/attachments/${attachmentId}/download`);
    return res.data.data;
  },

  async uploadCover(prototypeId: string, uploadId: string): Promise<void> {
    await http.post(`/prototypes/${prototypeId}/cover`, { uploadId });
  },
};
