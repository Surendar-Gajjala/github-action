import { useQuery } from '@tanstack/react-query';
import { employeesApi, queryKeys } from '../../api/employees';
import type { WorkflowResponse } from '../../api/types';

export const POLL_INTERVAL_MS = 2000;

export function isWorkflowActive(workflow?: WorkflowResponse): boolean {
  return workflow?.overallStatus === 'IN_PROGRESS';
}

/** Workflow status, polled every 2 s while the workflow is still running. */
export function useWorkflow(employeeId: number | null) {
  return useQuery({
    queryKey: queryKeys.workflow(employeeId ?? 0),
    queryFn: () => employeesApi.workflow(employeeId!),
    enabled: employeeId != null,
    refetchInterval: (query) => (isWorkflowActive(query.state.data) ? POLL_INTERVAL_MS : false),
  });
}

/** True while the selected workflow is running, so dependent views keep refreshing too. */
export function useWorkflowActive(employeeId: number | null): boolean {
  return isWorkflowActive(useWorkflow(employeeId).data);
}
