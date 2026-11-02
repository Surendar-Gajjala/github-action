// Types mirror the backend DTOs (com.gsr.scm.*Dtos). Dates are ISO-8601 strings.

export type LifecycleStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'CHANGE_PENDING';
export const LIFECYCLE_STATUSES: LifecycleStatus[] = ['DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'CHANGE_PENDING'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  path?: string;
  fieldErrors?: { field: string; message: string }[];
  details?: string[];
}

// ---------------------------------------------------------------- items

export interface ItemSummary {
  id: number;
  itemNumber: string;
  name: string;
  description?: string;
  revision: string;
  approvedRevision?: string;
  partCount: number;
  lifecycleStatus: LifecycleStatus;
  updatedAt: string;
  version: number;
}

export interface PartRef {
  id: number;
  partNumber: string;
  name: string;
  partType?: string;
  quantity: number;
}

export interface PartSnapshot {
  partId: number;
  partNumber: string;
  name: string;
  partType?: string;
  quantity: number;
}

export type ChangeRequestStatus = 'OPEN' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';

export interface ChangeRequest {
  id: number;
  number: string;
  status: ChangeRequestStatus;
  baseRevision?: string;
  targetRevision: string;
  changeLog?: string;
  rejectionReason?: string;
  createdAt: string;
  createdBy: string;
  submittedAt?: string;
  submittedBy?: string;
  decidedAt?: string;
  decidedBy?: string;
}

export interface ItemRevision {
  id: number;
  revision: string;
  name: string;
  description?: string;
  parts: PartSnapshot[];
  approvedAt: string;
  approvedBy: string;
  changeRequestId?: number;
  workflowInstanceId?: string;
}

export interface WorkflowRef {
  id: string;
  definitionKey: string;
  definitionVersion: number;
  status: InstanceStatus;
  currentStage?: string;
  startedAt: string;
}

export interface ItemDetail {
  id: number;
  itemNumber: string;
  name: string;
  description?: string;
  lifecycleStatus: LifecycleStatus;
  revision: string;
  approvedRevision?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  parts: PartRef[];
  currentChangeRequest?: ChangeRequest;
  lastApprovedRevision?: ItemRevision;
  latestWorkflow?: WorkflowRef;
  editable: boolean;
  submittable: boolean;
}

export interface FieldChange {
  field: string;
  oldValue?: string;
  newValue?: string;
}

export interface ChangeDiff {
  itemId: number;
  itemNumber: string;
  itemName: string;
  baseRevision?: string;
  targetRevision: string;
  fieldChanges: FieldChange[];
  partsAdded: PartSnapshot[];
  partsRemoved: PartSnapshot[];
  unchangedParts: PartSnapshot[];
  changeRequest?: ChangeRequest;
}

// ---------------------------------------------------------------- parts

export interface PartSummary {
  id: number;
  partNumber: string;
  name: string;
  description?: string;
  partType?: string;
  associatedItemsCount: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ItemRef {
  id: number;
  itemNumber: string;
  name: string;
  lifecycleStatus: LifecycleStatus;
  revision: string;
  approvedRevision?: string;
}

export interface PartDetail {
  id: number;
  partNumber: string;
  name: string;
  description?: string;
  partType?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  items: ItemRef[];
}

export interface AssociationResult {
  part: PartDetail;
  affectedItems: ItemRef[];
}

// ---------------------------------------------------------------- workflow

export type WorkflowType = 'ITEM_LIFECYCLE' | 'ITEM_PART_CHANGE' | 'REVISION_RELEASE';
export const WORKFLOW_TYPES: WorkflowType[] = ['ITEM_LIFECYCLE', 'ITEM_PART_CHANGE', 'REVISION_RELEASE'];
export type VersionStatus = 'DRAFT' | 'PUBLISHED' | 'SUPERSEDED';

export type ManagerApproval =
  | 'ALWAYS'
  | 'NEVER'
  | 'WHEN_PARTS_REMOVED'
  | 'WHEN_PREVIOUSLY_APPROVED'
  | 'WHEN_PART_CHANGES_EXCEED';

export interface ApprovalRules {
  managerApproval: ManagerApproval;
  partChangeThreshold: number;
  requiredRoles: string[];
  dueInDays?: number | null;
  preventSelfApproval: boolean;
}

export interface DefinitionSummary {
  key: string;
  name: string;
  description?: string;
  type: WorkflowType;
  active: boolean;
  latestVersion?: number;
  latestStatus?: VersionStatus;
  publishedVersion?: number;
  hasDraft: boolean;
  updatedAt: string;
  updatedBy?: string;
}

export interface VersionMeta {
  id: number;
  versionNumber: number;
  status: VersionStatus;
  engine?: string;
  engineProcessDefinitionKey?: number;
  engineVersion?: number;
  createdAt: string;
  createdBy?: string;
  updatedAt?: string;
  publishedAt?: string;
  publishedBy?: string;
}

export interface VersionDetail {
  meta: VersionMeta;
  bpmnXml: string;
  rules: ApprovalRules;
}

export interface DefinitionDetail {
  definition: DefinitionSummary;
  current?: VersionDetail;
  versions: VersionMeta[];
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
  processId?: string;
}

export interface WorkerInfo {
  jobType: string;
  description: string;
}

export type InstanceStatus = 'STARTING' | 'RUNNING' | 'COMPLETED' | 'REJECTED' | 'INCIDENT';

export interface InstanceSummary {
  id: string;
  engine: string;
  engineInstanceKey?: number;
  definitionKey: string;
  definitionName?: string;
  definitionVersion: number;
  businessKey: string;
  itemId: number;
  itemNumber: string;
  changeRequestId: number;
  currentStage?: string;
  status: InstanceStatus;
  managerApprovalRequired: boolean;
  startedBy: string;
  startedAt: string;
  updatedAt: string;
  endedAt?: string;
  incidentMessage?: string;
}

export type TaskStatus = 'OPEN' | 'APPROVED' | 'REJECTED';

export interface ApprovalTask {
  id: number;
  name: string;
  elementId: string;
  status: TaskStatus;
  candidateGroup: string;
  itemId: number;
  itemNumber: string;
  itemName: string;
  changeRequestId?: number;
  workflowInstanceId: string;
  engineInstanceKey?: number;
  definitionKey?: string;
  definitionVersion?: number;
  currentStage?: string;
  createdAt: string;
  dueDate?: string;
  completedAt?: string;
  decisionBy?: string;
  decisionComment?: string;
  overdue: boolean;
}

export interface WorkflowEvent {
  id: number;
  timestamp: string;
  actor: string;
  action: string;
  itemId?: number;
  itemNumber?: string;
  changeRequestId?: number;
  businessKey?: string;
  workflowInstanceId?: string;
  workflowDefinitionKey?: string;
  fromStatus?: string;
  toStatus?: string;
  summary?: string;
}

export interface InstanceDetail {
  instance: InstanceSummary;
  tasks: ApprovalTask[];
  history: WorkflowEvent[];
}

export interface LifecycleTransition {
  id: number;
  fromStatus: LifecycleStatus;
  toStatus: LifecycleStatus;
  enabled: boolean;
  system: boolean;
  description?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface EngineInfo {
  engine: 'DEV_ADAPTER' | 'CAMUNDA_8';
  label: string;
  realCamunda: boolean;
  connected: boolean;
  details: string;
}

export interface Me {
  username: string;
  roles: string[];
}
