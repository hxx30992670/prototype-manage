import { http } from '@/lib/http';

export interface PrototypeSpec {
  prototypePublicId: string;
  goal: string;
  coreFlow: string;
  interactionRules: string;
  businessConstraints: string;
  dataRequirements: string;
  acceptanceNotes: string;
  markdownExtra: string;
  rowVersion: number;
  updatedBy?: string;
  updatedAt: string;
}

export interface UpdateSpecPayload {
  goal: string;
  coreFlow: string;
  interactionRules: string;
  businessConstraints: string;
  dataRequirements: string;
  acceptanceNotes: string;
  markdownExtra: string;
  rowVersion: number;
}

export const specKeys = {
  detail: (prototypeId: string) => ['prototype-spec', prototypeId] as const,
};

export const specApi = {
  async get(prototypeId: string): Promise<PrototypeSpec> {
    const res = await http.get<{ data: PrototypeSpec }>(`/prototypes/${prototypeId}/spec`);
    return res.data.data;
  },

  async update(prototypeId: string, payload: UpdateSpecPayload): Promise<PrototypeSpec> {
    const res = await http.put<{ data: PrototypeSpec }>(`/prototypes/${prototypeId}/spec`, payload);
    return res.data.data;
  },
};
