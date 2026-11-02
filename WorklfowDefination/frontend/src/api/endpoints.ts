import { api, query } from './client';
import type {
  ApprovalRules,
  ApprovalTask,
  AssociationResult,
  ChangeDiff,
  DefinitionDetail,
  DefinitionSummary,
  EngineInfo,
  InstanceDetail,
  InstanceStatus,
  InstanceSummary,
  ItemDetail,
  ItemRevision,
  ItemSummary,
  LifecycleStatus,
  LifecycleTransition,
  Me,
  Page,
  PageQuery,
  PartDetail,
  PartRef,
  PartSummary,
  TaskStatus,
  ValidationResult,
  VersionDetail,
  VersionMeta,
  WorkerInfo,
  WorkflowEvent,
  WorkflowType,
} from './types';

export const itemsApi = {
  list: (p: PageQuery & { status?: LifecycleStatus }) => api.get<Page<ItemSummary>>(`/api/items${query(p)}`),
  get: (id: number) => api.get<ItemDetail>(`/api/items/${id}`),
  create: (body: { itemNumber: string; name: string; description?: string; partIds: number[] }) =>
    api.post<ItemDetail>('/api/items', body),
  update: (id: number, body: { name: string; description?: string; partIds?: number[]; version: number }) =>
    api.put<ItemDetail>(`/api/items/${id}`, body),
  parts: (id: number) => api.get<PartRef[]>(`/api/items/${id}/parts`),
  replaceParts: (id: number, partIds: number[], version: number) =>
    api.put<ItemDetail>(`/api/items/${id}/parts`, { partIds, version }),
  submit: (id: number, body: { workflowKey?: string; version?: number }) =>
    api.post<InstanceSummary>(`/api/items/${id}/submit-for-approval`, body),
  history: (id: number) => api.get<WorkflowEvent[]>(`/api/items/${id}/workflow-history`),
  revisions: (id: number) => api.get<ItemRevision[]>(`/api/items/${id}/revisions`),
  changes: (id: number) => api.get<ChangeDiff>(`/api/items/${id}/changes`),
};

export const partsApi = {
  list: (p: PageQuery & { partType?: string }) => api.get<Page<PartSummary>>(`/api/parts${query(p)}`),
  get: (id: number) => api.get<PartDetail>(`/api/parts/${id}`),
  create: (body: { partNumber: string; name: string; description?: string; partType?: string; itemIds: number[] }) =>
    api.post<AssociationResult>('/api/parts', body),
  update: (id: number, body: { name: string; description?: string; partType?: string; version: number }) =>
    api.put<PartDetail>(`/api/parts/${id}`, body),
  replaceItems: (id: number, itemIds: number[]) => api.put<AssociationResult>(`/api/parts/${id}/items`, { itemIds }),
};

export const definitionsApi = {
  list: () => api.get<DefinitionSummary[]>('/api/workflows/definitions'),
  get: (key: string) => api.get<DefinitionDetail>(`/api/workflows/definitions/${key}`),
  create: (body: {
    key: string;
    name: string;
    description?: string;
    type: WorkflowType;
    bpmnXml?: string;
    rules?: ApprovalRules;
  }) => api.post<DefinitionDetail>('/api/workflows/definitions', body),
  saveDraft: (
    key: string,
    body: { name?: string; description?: string; type?: WorkflowType; bpmnXml: string; rules: ApprovalRules },
  ) => api.put<DefinitionDetail>(`/api/workflows/definitions/${key}`, body),
  publish: (key: string) => api.post<DefinitionDetail>(`/api/workflows/definitions/${key}/publish`),
  activate: (key: string) => api.post<DefinitionDetail>(`/api/workflows/definitions/${key}/activate`),
  deactivate: (key: string) => api.post<DefinitionDetail>(`/api/workflows/definitions/${key}/deactivate`),
  versions: (key: string) => api.get<VersionMeta[]>(`/api/workflows/definitions/${key}/versions`),
  version: (key: string, v: number) => api.get<VersionDetail>(`/api/workflows/definitions/${key}/versions/${v}`),
  validate: (bpmnXml: string, key?: string) =>
    api.post<ValidationResult>('/api/workflows/definitions/validate', { bpmnXml, key }),
  workers: () => api.get<WorkerInfo[]>('/api/workflows/definitions/workers'),
};

export const instancesApi = {
  list: (p: PageQuery & { status?: InstanceStatus; definitionKey?: string; activeOnly?: boolean }) =>
    api.get<Page<InstanceSummary>>(`/api/workflows/instances${query(p)}`),
  get: (id: string) => api.get<InstanceDetail>(`/api/workflows/instances/${id}`),
  history: (id: string) => api.get<WorkflowEvent[]>(`/api/workflows/instances/${id}/history`),
  start: (itemId: number, workflowKey?: string) =>
    api.post<InstanceSummary>('/api/workflows/instances', { itemId, workflowKey }),
  retry: (id: string) => api.post<InstanceSummary>(`/api/workflows/instances/${id}/retry`),
};

export const tasksApi = {
  list: (p: PageQuery & { status?: TaskStatus; mine?: boolean }) =>
    api.get<Page<ApprovalTask>>(`/api/workflows/tasks${query(p)}`),
  changes: (id: number) => api.get<ChangeDiff>(`/api/workflows/tasks/${id}/changes`),
  approve: (id: number, comment?: string) => api.post<ApprovalTask>(`/api/workflows/tasks/${id}/approve`, { comment }),
  reject: (id: number, reason: string) => api.post<ApprovalTask>(`/api/workflows/tasks/${id}/reject`, { reason }),
};

export const workflowApi = {
  history: (p: PageQuery & { action?: string }) => api.get<Page<WorkflowEvent>>(`/api/workflows/history${query(p)}`),
  transitions: () => api.get<LifecycleTransition[]>('/api/workflows/lifecycle-transitions'),
  upsertTransition: (body: { fromStatus: LifecycleStatus; toStatus: LifecycleStatus; enabled: boolean; description?: string }) =>
    api.put<LifecycleTransition>('/api/workflows/lifecycle-transitions', body),
  deleteTransition: (id: number) => api.delete<void>(`/api/workflows/lifecycle-transitions/${id}`),
  engine: () => api.get<EngineInfo>('/api/workflows/engine'),
  me: () => api.get<Me>('/api/me'),
};
