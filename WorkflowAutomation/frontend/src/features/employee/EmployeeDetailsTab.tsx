import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { useForm, type FieldError, type UseFormRegisterReturn } from 'react-hook-form';
import { ApiError } from '../../api/client';
import { employeesApi } from '../../api/employees';
import type { EmployeeCreatedResponse } from '../../api/types';
import { Button, Card, ErrorMessage } from '../../components/ui';
import { ACCESS_LEVELS, createEmployeeSchema, EMPTY_EMPLOYEE, type EmployeeFormValues } from './employeeSchema';

interface Props {
  onSubmitted: (created: EmployeeCreatedResponse) => void;
}

const ACCESS_DESCRIPTIONS: Record<(typeof ACCESS_LEVELS)[number], string> = {
  READ: 'Read — view and clone',
  TRIAGE: 'Triage — manage issues and pull requests',
  WRITE: 'Write — push to the repository',
  MAINTAIN: 'Maintain — manage the repository without admin settings',
  ADMIN: 'Admin — full control',
};

export function EmployeeDetailsTab({ onSubmitted }: Props) {
  const schema = useMemo(() => createEmployeeSchema(), []);
  const queryClient = useQueryClient();
  const [created, setCreated] = useState<EmployeeCreatedResponse | null>(null);
  // One key per logical submission: a network retry of the same submission reuses it, so the server never
  // creates a duplicate employee. A fresh key is issued after a successful submit or a reset.
  const idempotencyKey = useRef(crypto.randomUUID());

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<EmployeeFormValues>({ resolver: zodResolver(schema), defaultValues: EMPTY_EMPLOYEE, mode: 'onTouched' });

  const mutation = useMutation({
    mutationFn: (values: EmployeeFormValues) => employeesApi.create(values, idempotencyKey.current),
    onSuccess: (response) => {
      setCreated(response);
      idempotencyKey.current = crypto.randomUUID();
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      reset(EMPTY_EMPLOYEE);
      onSubmitted(response);
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        error.problem.errors?.forEach(({ field, message }) =>
          setError(field as keyof EmployeeFormValues, { type: 'server', message }),
        );
      }
    },
  });

  function onReset() {
    reset(EMPTY_EMPLOYEE);
    mutation.reset();
    setCreated(null);
    idempotencyKey.current = crypto.randomUUID();
  }

  return (
    <Card title="New employee">
      <form onSubmit={handleSubmit((values) => mutation.mutate(values))} noValidate className="space-y-6">
        <div className="grid gap-5 sm:grid-cols-2">
          <FormField label="Employee ID" htmlFor="employeeId">
            <input
              id="employeeId"
              className="input"
              disabled
              value={created ? created.employeeNumber : ''}
              placeholder="Generated automatically on submit"
            />
          </FormField>
          <div className="hidden sm:block" />
          <TextField label="First Name" id="firstName" registration={register('firstName')} error={errors.firstName} autoComplete="off" />
          <TextField label="Last Name" id="lastName" registration={register('lastName')} error={errors.lastName} autoComplete="off" />
          <TextField label="Work Email" id="workEmail" type="email" registration={register('workEmail')} error={errors.workEmail} placeholder="name@company.com" />
          <TextField label="Job Title" id="jobTitle" registration={register('jobTitle')} error={errors.jobTitle} />
          <TextField label="Department" id="department" registration={register('department')} error={errors.department} />
          <TextField label="Joining Date" id="joiningDate" type="date" registration={register('joiningDate')} error={errors.joiningDate} />
          <TextField label="Reporting Manager" id="reportingManager" registration={register('reportingManager')} error={errors.reportingManager} />
          <TextField label="GitHub Username" id="githubUsername" registration={register('githubUsername')} error={errors.githubUsername} placeholder="octocat" />
          <TextField label="Required GitHub Repository" id="githubRepository" registration={register('githubRepository')} error={errors.githubRepository} placeholder="owner/repository" />
          <FormField label="Role or Access Level" htmlFor="accessLevel" error={errors.accessLevel}>
            <select id="accessLevel" className={`input ${errors.accessLevel ? 'input-error' : ''}`} {...register('accessLevel')}>
              {ACCESS_LEVELS.map((level) => (
                <option key={level} value={level}>
                  {ACCESS_DESCRIPTIONS[level]}
                </option>
              ))}
            </select>
          </FormField>
        </div>

        <p className="text-xs text-slate-500">
          Submitting starts the onboarding workflow automatically: the welcome email is generated and sent and a GitHub
          onboarding issue is created. Creating the issue does not grant repository access.
        </p>

        {mutation.isError && <ErrorMessage error={mutation.error} />}
        {created && !mutation.isPending && (
          <div role="status" className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
            {created.duplicateRequest ? 'This request was already submitted' : 'Employee submitted'}: <strong>{created.employeeNumber}</strong>.
            Workflow #{created.workflowId} is running.
          </div>
        )}

        <div className="flex flex-wrap gap-3">
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Submitting…' : 'Submit Employee'}
          </Button>
          <Button variant="secondary" onClick={onReset} disabled={mutation.isPending}>
            Reset Form
          </Button>
        </div>
      </form>
    </Card>
  );
}

function FormField({
  label,
  htmlFor,
  error,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: FieldError;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {error && (
        <p id={`${htmlFor}-error`} className="mt-1 text-xs text-red-600">
          {error.message}
        </p>
      )}
    </div>
  );
}

function TextField({
  label,
  id,
  registration,
  error,
  type = 'text',
  placeholder,
  autoComplete,
}: {
  label: string;
  id: string;
  registration: UseFormRegisterReturn;
  error?: FieldError;
  type?: string;
  placeholder?: string;
  autoComplete?: string;
}) {
  return (
    <FormField label={label} htmlFor={id} error={error}>
      <input
        id={id}
        type={type}
        placeholder={placeholder}
        autoComplete={autoComplete}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`input ${error ? 'input-error' : ''}`}
        {...registration}
      />
    </FormField>
  );
}
