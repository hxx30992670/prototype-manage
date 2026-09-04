import { http } from '@/lib/http';

export interface PrototypeQuery {
  keyword?: string;
  categoryId?: string;
  tagId?: string;
  reviewStatus?: string;
  archived?: boolean;
  createdBy?: string;
  ownerId?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrder?: string;
}

export interface UserRef {
  publicId: string;
  username: string;
  displayName: string;
}

export interface PrototypeItem {
  publicId: string;
  code: string;
  name: string;
  description?: string;
  publicSummary?: string;
  visibility: string;
  reviewStatus: string;
  archived: boolean;
  category?: { code: string; name: string };
  createdBy?: UserRef;
  owner?: UserRef;
  owners?: UserRef[];
  tags: Array<{ name: string; color?: string }>;
  rowVersion: number;
  createdAt: string;
  updatedAt: string;
  currentVersionNo?: number;
  currentVersionStatus?: string;
}

export function prototypeOwners(item: Pick<PrototypeItem, 'owner' | 'owners'>): UserRef[] {
  if (item.owners && item.owners.length > 0) {
    return item.owners;
  }
  return item.owner ? [item.owner] : [];
}

export function isPrototypeOwner(item: Pick<PrototypeItem, 'owner' | 'owners'>, userPublicId?: string): boolean {
  if (!userPublicId) return false;
  return prototypeOwners(item).some((owner) => owner.publicId === userPublicId);
}

export function ownerNames(item: Pick<PrototypeItem, 'owner' | 'owners'>): string {
  const names = prototypeOwners(item).map((owner) => owner.displayName).filter(Boolean);
  return names.length ? names.join('、') : '-';
}

export interface PrototypePageResponse {
  data: PrototypeItem[];
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
}

export interface CreatePrototypePayload {
  code: string;
  name: string;
  description?: string;
  publicSummary?: string;
  categoryId: string;
  ownerId?: string;
  ownerIds?: string[];
  visibility?: string;
  tagIds?: string[];
}

export interface UpdatePrototypePayload {
  name: string;
  description?: string;
  publicSummary?: string;
  categoryId: string;
  ownerId?: string;
  ownerIds?: string[];
  visibility?: string;
  tagIds?: string[];
}

export interface CategoryItem {
  code: string;
  name: string;
  sortNo: number;
}

export interface TagItem {
  name: string;
  color?: string;
}

export const prototypeKeys = {
  all: ['prototypes'] as const,
  list: (query: PrototypeQuery) => ['prototypes', 'list', query] as const,
  detail: (id: string) => ['prototypes', 'detail', id] as const,
};

export const prototypeApi = {
  async list(query: PrototypeQuery): Promise<PrototypePageResponse> {
    const res = await http.get<PrototypePageResponse>('/prototypes', { params: query });
    return res.data;
  },

  async get(id: string): Promise<PrototypeItem> {
    const res = await http.get<{ data: PrototypeItem }>(`/prototypes/${id}`);
    return res.data.data;
  },

  async create(payload: CreatePrototypePayload): Promise<PrototypeItem> {
    const res = await http.post<{ data: PrototypeItem }>('/prototypes', payload);
    return res.data.data;
  },

  async update(id: string, payload: UpdatePrototypePayload, rowVersion: number): Promise<PrototypeItem> {
    const res = await http.put<{ data: PrototypeItem }>(`/prototypes/${id}`, payload, {
      headers: {
        'If-Match': `"${rowVersion}"`,
      },
    });
    return res.data.data;
  },

  async updateReviewStatus(id: string, reviewStatus: string, note?: string): Promise<PrototypeItem> {
    const res = await http.put<{ data: PrototypeItem }>(`/prototypes/${id}/review-status`, {
      reviewStatus,
      note,
    });
    return res.data.data;
  },

  async archive(id: string, archived: boolean): Promise<PrototypeItem> {
    const res = await http.put<{ data: PrototypeItem }>(`/prototypes/${id}/archive`, { archived });
    return res.data.data;
  },

  async delete(id: string): Promise<void> {
    await http.delete(`/prototypes/${id}`);
  },

  async listCategories(): Promise<CategoryItem[]> {
    const res = await http.get<{ data: CategoryItem[] }>('/categories');
    return res.data.data;
  },

  async listTags(): Promise<TagItem[]> {
    const res = await http.get<{ data: TagItem[] }>('/tags');
    return res.data.data;
  },

  async listAssignableOwners(): Promise<UserRef[]> {
    const res = await http.get<{ data: UserRef[] }>('/users/assignable-owners');
    return res.data.data;
  },
};
