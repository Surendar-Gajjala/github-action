import { createEmployeeSchema, type EmployeeFormValues } from './employeeSchema';

const today = new Date(2026, 9, 26); // 26 Oct 2026
const schema = createEmployeeSchema(today);

const valid: EmployeeFormValues = {
  firstName: 'Priya',
  lastName: "O'Neil-Sharma",
  workEmail: 'priya.sharma@example.com',
  jobTitle: 'Backend Engineer',
  department: 'Engineering',
  joiningDate: '2026-11-16',
  reportingManager: 'Daniel Okafor',
  githubUsername: 'priya-sharma',
  githubRepository: 'acme-corp/payments-service',
  accessLevel: 'WRITE',
};

function errorsFor(values: Partial<EmployeeFormValues>) {
  const result = schema.safeParse({ ...valid, ...values });
  return result.success ? {} : result.error.flatten().fieldErrors;
}

describe('employee form schema', () => {
  it('accepts a valid employee', () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  it('requires all mandatory fields', () => {
    const result = schema.safeParse({ ...valid, firstName: ' ', department: '', reportingManager: '' });
    expect(result.success).toBe(false);
    const errors = result.success ? {} : result.error.flatten().fieldErrors;
    expect(errors.firstName).toBeDefined();
    expect(errors.department).toBeDefined();
    expect(errors.reportingManager).toBeDefined();
  });

  it('validates email format', () => {
    expect(errorsFor({ workEmail: 'not-an-email' }).workEmail?.[0]).toMatch(/valid email/);
  });

  it('limits the joining date window', () => {
    expect(errorsFor({ joiningDate: '2026-09-25' }).joiningDate).toBeDefined();
    expect(errorsFor({ joiningDate: '2027-10-27' }).joiningDate).toBeDefined();
    expect(errorsFor({ joiningDate: '2026-09-26' }).joiningDate).toBeUndefined();
  });

  it('validates GitHub username and repository', () => {
    expect(errorsFor({ githubUsername: '-bad' }).githubUsername).toBeDefined();
    expect(errorsFor({ githubUsername: 'double--hyphen' }).githubUsername).toBeDefined();
    expect(errorsFor({ githubUsername: 'a'.repeat(40) }).githubUsername).toBeDefined();
    expect(errorsFor({ githubRepository: 'no-slash' }).githubRepository).toBeDefined();
    expect(errorsFor({ githubRepository: 'owner/repo.js' }).githubRepository).toBeUndefined();
  });
});
