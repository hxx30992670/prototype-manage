import axios from 'axios';
import { http } from '@/lib/http';

export interface VersionItem {
  versionId: string;
  versionNo: number;
  changeLog: string;
  description?: string;
  status: 'PUBLISHING' | 'PUBLISHED' | 'FAILED';
  sourceType: 'ZIP' | 'HTML';
  sourceSize: number;
  entryPath?: string;
  fileCount: number;
  expandedSize: number;
  isCurrent: boolean;
  failureStage?: string;
  failureMessage?: string;
  createdBy?: string;
  createdAt: string;
  publishedAt?: string;
}

export interface PublishJobStatus {
  jobId: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED';
  stage?: 'UPLOAD_CHECK' | 'PACKAGE_CHECK' | 'EXTRACT' | 'STORE' | 'FINALIZE';
  progress: number;
  errorDetail?: string;
  createdAt: string;
  finishedAt?: string;
}

export interface CreateUploadResponse {
  uploadId: string;
  uploadUrl: string;
  expiresAt: string;
}

export interface CreateVersionPayload {
  uploadId: string;
  sourceType: 'ZIP' | 'HTML';
  changeLog: string;
  description?: string;
}

export interface SwitchVersionPayload {
  expectedCurrentVersionNo?: number;
  reason: string;
}

export const versionKeys = {
  list: (prototypeId: string) => ['versions', 'list', prototypeId] as const,
  detail: (prototypeId: string, versionId: string) => ['versions', 'detail', prototypeId, versionId] as const,
  job: (prototypeId: string, versionId: string) => ['versions', 'job', prototypeId, versionId] as const,
};

export const versionApi = {
  async createUpload(filename: string, claimedSize: number, fileType: 'ZIP' | 'HTML'): Promise<CreateUploadResponse> {
    const res = await http.post<{ data: CreateUploadResponse }>('/uploads', {
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

  async completeUpload(uploadId: string, checksum?: string): Promise<void> {
    await http.post(`/uploads/${uploadId}/complete`, { checksum });
  },

  async createVersion(prototypeId: string, payload: CreateVersionPayload): Promise<{ versionId: string; versionNo: number; status: string; jobId: number }> {
    const res = await http.post<{ data: { versionId: string; versionNo: number; status: string; jobId: number } }>(
      `/prototypes/${prototypeId}/versions`,
      payload
    );
    return res.data.data;
  },

  async list(prototypeId: string): Promise<VersionItem[]> {
    const res = await http.get<{ data: VersionItem[] }>(`/prototypes/${prototypeId}/versions`);
    return res.data.data;
  },

  async get(prototypeId: string, versionId: string): Promise<VersionItem> {
    const res = await http.get<{ data: VersionItem }>(`/prototypes/${prototypeId}/versions/${versionId}`);
    return res.data.data;
  },

  async getPublishJob(prototypeId: string, versionId: string): Promise<PublishJobStatus> {
    const res = await http.get<{ data: PublishJobStatus }>(`/prototypes/${prototypeId}/versions/${versionId}/publish-job`);
    return res.data.data;
  },

  async switchCurrent(prototypeId: string, versionId: string, payload: SwitchVersionPayload): Promise<VersionItem> {
    const res = await http.post<{ data: VersionItem }>(`/prototypes/${prototypeId}/versions/${versionId}/switch`, payload);
    return res.data.data;
  },

  async createDownloadTicket(prototypeId: string, versionId: string): Promise<{ url: string; expiresAt: string }> {
    const res = await http.post<{ data: { url: string; expiresAt: string } }>(
      `/prototypes/${prototypeId}/versions/${versionId}/download-ticket`
    );
    return res.data.data;
  },

  async createPreviewTicket(
    prototypeId: string,
    versionId?: string
  ): Promise<{ ticket: string; contentUrl: string; expiresAt: string }> {
    const path = versionId
      ? `/prototypes/${prototypeId}/versions/${versionId}/preview-ticket`
      : `/prototypes/${prototypeId}/preview-ticket`;
    const res = await http.post<{ data: { ticket: string; contentUrl: string; expiresAt: string } }>(path);
    return res.data.data;
  },

  async delete(prototypeId: string, versionId: string): Promise<void> {
    await http.delete(`/prototypes/${prototypeId}/versions/${versionId}`);
  },
};
