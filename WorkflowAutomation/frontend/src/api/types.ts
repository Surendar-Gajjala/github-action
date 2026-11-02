// Mirrors the backend DTOs in com.gsr.onboarding.dto.

export type AccessLevel = 'READ' | 'TRIAGE' | 'WRITE' | 'MAINTAIN' | 'ADMIN';
export type WorkflowStatus = 'IN_PROGRESS' | 'AWAITING_APPROVAL' | 'COMPLETED' | 'FAILED';
export type StepStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'SUCCESS'
  | 'FAILED'
  | 'FAILED_UNCERTAIN'
  | 'AWAITING_APPROVAL'
  | 'SKIPPED';
export type StepName =
  | 'VALIDATE_DETAILS'
  | 'SAVE_RECORD'
  | 'GENERATE_CONTENT'
  | 'SEND_EMAIL'
  | 'CREATE_GITHUB_ISSUE'
  | 'COMPLETE_WORKFLOW';
export type ArtifactStatus = 'DRAFT' | 'APPROVED' | 'SENDING' | 'SENT' | 'SEND_FAILED' | 'CREATED' | 'FAILED';
export type ContentSource = 'AI' | 'FALLBACK' | 'EDITED';

export interface EmployeeRequest {
  firstName: string;
  lastName: string;
  workEmail: string;
  jobTitle: string;
  department: string;
  joiningDate: string;
  reportingManager: string;
  githubUsername: string;
  githubRepository: string;
  accessLevel: AccessLevel;
}

export interface EmployeeCreatedResponse {
  employeeId: number;
  employeeNumber: string;
  workflowId: number;
  workflowStatus: WorkflowStatus;
  duplicateRequest: boolean;
}

export interface EmployeeResponse {
  id: number;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  jobTitle: string;
  department: string;
  joiningDate: string;
  reportingManager: string;
  githubUsername: string;
  githubRepository: string;
  accessLevel: AccessLevel;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeSummary {
  id: number;
  employeeNumber: string;
  fullName: string;
  email: string;
  department: string;
  joiningDate: string;
  workflowStatus?: WorkflowStatus;
  emailStatus?: StepStatus;
  githubIssueStatus?: StepStatus;
  retryable: boolean;
  createdAt: string;
}

export interface EmployeeDetail {
  employee: EmployeeResponse;
  workflow: WorkflowResponse;
}

export interface StepResponse {
  name: StepName;
  label: string;
  order: number;
  status: StepStatus;
  attempts: number;
  startedAt?: string;
  completedAt?: string;
  errorMessage?: string;
  externalReference?: string;
}

export interface HistoryEntry {
  at: string;
  actor: string;
  action: string;
  details?: string;
}

export interface WorkflowResponse {
  workflowId: number;
  employeeId: number;
  overallStatus: WorkflowStatus;
  currentStep?: StepName;
  emailApprovalRequired: boolean;
  startedAt: string;
  completedAt?: string;
  lastError?: string;
  retryable: boolean;
  requiresForcedRetry: boolean;
  steps: StepResponse[];
  history: HistoryEntry[];
}

export interface EmailPreview {
  available: boolean;
  recipient?: string;
  subject?: string;
  body?: string;
  checklist: string[];
  status?: ArtifactStatus;
  contentSource?: ContentSource;
  stepStatus?: StepStatus;
  approvalRequired: boolean;
  awaitingApproval: boolean;
  sentAt?: string;
}

export interface EmailApprovalRequest {
  subject?: string;
  body?: string;
}

export interface GitHubIssue {
  available: boolean;
  repository?: string;
  requestedRepository?: string;
  issueTitle?: string;
  issueBody?: string;
  checklist: string[];
  issueNumber?: number;
  issueUrl?: string;
  status?: ArtifactStatus;
  stepStatus?: StepStatus;
  errorMessage?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  correlationId?: string;
  errors?: { field: string; message: string }[];
}
