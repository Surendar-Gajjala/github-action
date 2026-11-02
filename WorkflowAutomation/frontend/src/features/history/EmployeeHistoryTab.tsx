import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { employeesApi, queryKeys } from '../../api/employees';
import { StatusBadge } from '../../components/StatusBadge';
import { Button, Card, EmptyState, ErrorMessage } from '../../components/ui';
import { formatDate, formatDateTime } from '../../lib/format';

const PAGE_SIZE = 10;

function useDebounced<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}

export function EmployeeHistoryTab({ onOpen }: { onOpen: (employeeId: number) => void }) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const query = useDebounced(search, 300);

  useEffect(() => setPage(0), [query]);

  const employees = useQuery({
    queryKey: queryKeys.employees(query, page),
    queryFn: () => employeesApi.list(query, page, PAGE_SIZE),
    placeholderData: keepPreviousData,
    refetchInterval: (q) =>
      q.state.data?.content.some((e) => e.workflowStatus === 'IN_PROGRESS') ? 3000 : false,
  });

  const retry = useMutation({
    mutationFn: (id: number) => employeesApi.retry(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: queryKeys.workflow(id) });
    },
  });

  const data = employees.data;

  return (
    <Card
      title="Employee history"
      actions={
        <input
          type="search"
          aria-label="Search employees"
          placeholder="Search name, email, ID, department…"
          className="input w-72 max-w-full"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      }
    >
      {employees.isError && <ErrorMessage error={employees.error} />}
      {retry.isError && (
        <div className="mb-3">
          <ErrorMessage error={retry.error} />
        </div>
      )}
      {data && data.content.length === 0 && <EmptyState>No employees match your search.</EmptyState>}

      {data && data.content.length > 0 && (
        <div className="-mx-5 overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                {['Employee ID', 'Employee Name', 'Email', 'Department', 'Joining Date', 'Workflow', 'Email', 'GitHub Issue', 'Created', ''].map(
                  (heading, i) => (
                    <th key={i} scope="col" className="whitespace-nowrap px-4 py-3">
                      {heading}
                    </th>
                  ),
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((employee) => (
                <tr key={employee.id} className="hover:bg-slate-50">
                  <td className="whitespace-nowrap px-4 py-3 font-mono text-xs">{employee.employeeNumber}</td>
                  <td className="whitespace-nowrap px-4 py-3 font-medium text-slate-900">{employee.fullName}</td>
                  <td className="px-4 py-3 text-slate-600">{employee.email}</td>
                  <td className="whitespace-nowrap px-4 py-3">{employee.department}</td>
                  <td className="whitespace-nowrap px-4 py-3">{formatDate(employee.joiningDate)}</td>
                  <td className="px-4 py-3"><StatusBadge status={employee.workflowStatus} /></td>
                  <td className="px-4 py-3"><StatusBadge status={employee.emailStatus} /></td>
                  <td className="px-4 py-3"><StatusBadge status={employee.githubIssueStatus} /></td>
                  <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-500">{formatDateTime(employee.createdAt)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      {employee.retryable && (
                        <Button
                          variant="secondary"
                          className="px-2.5 py-1 text-xs"
                          disabled={retry.isPending && retry.variables === employee.id}
                          onClick={() => retry.mutate(employee.id)}
                        >
                          Retry
                        </Button>
                      )}
                      <Button className="px-2.5 py-1 text-xs" onClick={() => onOpen(employee.id)}>
                        Open
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <nav className="mt-4 flex items-center justify-between text-sm" aria-label="Pagination">
          <span className="text-slate-500">
            Page {data.page + 1} of {data.totalPages} · {data.totalElements} employees
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </nav>
      )}
    </Card>
  );
}
