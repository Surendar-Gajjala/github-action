import { z } from 'zod';

// Mirrors com.gsr.onboarding.dto.ValidationPatterns and JoiningDateValidator on the backend.
const PERSON_NAME = /^\p{L}[\p{L} .'-]*$/u;
const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const GITHUB_USERNAME = /^(?=.{1,39}$)[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9]))*$/;
const GITHUB_REPOSITORY = /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})\/[A-Za-z0-9._-]{1,100}$/;

export const MAX_DAYS_IN_PAST = 30;
export const MAX_DAYS_IN_FUTURE = 365;

export const ACCESS_LEVELS = ['READ', 'TRIAGE', 'WRITE', 'MAINTAIN', 'ADMIN'] as const;

function isoDay(offsetDays: number, today = new Date()): string {
  const d = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate() + offsetDays));
  return d.toISOString().slice(0, 10);
}

const requiredText = (label: string, max: number) =>
  z.string().trim().min(1, `${label} is required`).max(max, `${label} must be at most ${max} characters`);

const personName = (label: string) =>
  requiredText(label, 100).regex(PERSON_NAME, `${label} may contain letters, spaces, apostrophes, dots or hyphens only`);

export function createEmployeeSchema(today = new Date()) {
  const earliest = isoDay(-MAX_DAYS_IN_PAST, today);
  const latest = isoDay(MAX_DAYS_IN_FUTURE, today);
  return z.object({
    firstName: personName('First name'),
    lastName: personName('Last name'),
    workEmail: requiredText('Work email', 254).regex(EMAIL, 'Enter a valid email address'),
    jobTitle: requiredText('Job title', 150),
    department: requiredText('Department', 100),
    joiningDate: z
      .string()
      .min(1, 'Joining date is required')
      .regex(/^\d{4}-\d{2}-\d{2}$/, 'Enter a valid date')
      .refine((value) => value >= earliest && value <= latest, {
        message: `Joining date must be between ${earliest} and ${latest}`,
      }),
    reportingManager: requiredText('Reporting manager', 150),
    githubUsername: z
      .string()
      .trim()
      .min(1, 'GitHub username is required')
      .regex(GITHUB_USERNAME, 'Use 1-39 letters, digits or single hyphens (no leading/trailing hyphen)'),
    githubRepository: requiredText('Repository', 200).regex(GITHUB_REPOSITORY, 'Use the form owner/repository'),
    accessLevel: z.enum(ACCESS_LEVELS, { errorMap: () => ({ message: 'Select an access level' }) }),
  });
}

export type EmployeeFormValues = z.infer<ReturnType<typeof createEmployeeSchema>>;

export const EMPTY_EMPLOYEE: EmployeeFormValues = {
  firstName: '',
  lastName: '',
  workEmail: '',
  jobTitle: '',
  department: '',
  joiningDate: '',
  reportingManager: '',
  githubUsername: '',
  githubRepository: '',
  accessLevel: 'WRITE',
};
