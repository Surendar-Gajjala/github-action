import { apiFetch } from './client';
import type {
  EmailApprovalRequest,
  EmailPreview,
  EmployeeCreatedResponse,
  EmployeeDetail,
  EmployeeRequest,
  EmployeeSummary,
  GitHubIssue,
  Page,
  WorkflowResponse,
} from './types';

export const employeesApi = {
  create(request: EmployeeRequest, idempotencyKey: string) {
    return apiFetch<EmployeeCreatedResponse>('/api/employees', {
      method: 'POST',
      body: JSON.stringify(request),
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  },

  list(query: string, page: number, size: number) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (query.trim()) params.set('q', query.trim());
    return apiFetch<Page<EmployeeSummary>>(`/api/employees?${params}`);
  },

  get(id: number) {
    return apiFetch<EmployeeDetail>(`/api/employees/${id}`);
  },

  workflow(id: number) {
    return apiFetch<WorkflowResponse>(`/api/employees/${id}/workflow`);
  },

  retry(id: number, force = false) {
    return apiFetch<WorkflowResponse>(`/api/employees/${id}/workflow/retry${force ? '?force=true' : ''}`, {
      method: 'POST',
    });
  },

  emailPreview(id: number) {
    return apiFetch<EmailPreview>(`/api/employees/${id}/email-preview`);
  },

  approveEmail(id: number, request: EmailApprovalRequest) {
    return apiFetch<EmailPreview>(`/api/employees/${id}/email/approve`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  githubIssue(id: number) {
    return apiFetch<GitHubIssue>(`/api/employees/${id}/github-issue`);
  },

  /** Verifies credentials by calling a cheap authenticated endpoint. */
  ping() {
    return apiFetch<Page<EmployeeSummary>>('/api/employees?page=0&size=1');
  },
};

export const queryKeys = {
  employees: (query: string, page: number) => ['employees', query, page] as const,
  employee: (id: number) => ['employee', id] as const,
  workflow: (id: number) => ['workflow', id] as const,
  email: (id: number) => ['email', id] as const,
  github: (id: number) => ['github', id] as const,
};
