-- Realistic sample onboarding data for local development (not loaded by the test profile).
-- Uses INSERT ... SELECT with natural keys so identity sequences stay consistent on H2 and PostgreSQL.

INSERT INTO employees (employee_number, first_name, last_name, email, job_title, department, joining_date,
                       reporting_manager, github_username, github_repository, access_level,
                       created_by, updated_by, created_at, updated_at)
VALUES
    ('EMP-000001', 'Aisha', 'Khan', 'aisha.khan@example.com', 'Senior Backend Engineer', 'Engineering', DATE '2026-10-05',
     'Daniel Okafor', 'aisha-khan', 'acme-corp/payments-service', 'WRITE', 'hr', 'hr',
     TIMESTAMP WITH TIME ZONE '2026-09-28 09:15:00+00', TIMESTAMP WITH TIME ZONE '2026-09-28 09:15:00+00'),
    ('EMP-000002', 'Mateo', 'Rossi', 'mateo.rossi@example.com', 'Product Designer', 'Design', DATE '2026-10-12',
     'Sofia Lindqvist', 'mateo-rossi', 'acme-corp/design-system', 'TRIAGE', 'hr', 'hr',
     TIMESTAMP WITH TIME ZONE '2026-10-02 10:30:00+00', TIMESTAMP WITH TIME ZONE '2026-10-02 10:30:00+00'),
    ('EMP-000003', 'Hannah', 'Weber', 'hannah.weber@example.com', 'Data Analyst', 'Analytics', DATE '2026-10-19',
     'Ravi Menon', 'hweber', 'acme-corp/analytics-pipelines', 'READ', 'hr', 'hr',
     TIMESTAMP WITH TIME ZONE '2026-10-08 14:05:00+00', TIMESTAMP WITH TIME ZONE '2026-10-08 14:05:00+00'),
    ('EMP-000004', 'Kwame', 'Mensah', 'kwame.mensah@example.com', 'DevOps Engineer', 'Platform', DATE '2026-11-02',
     'Laura Chen', 'kmensah', 'acme-corp/infrastructure', 'MAINTAIN', 'hr', 'hr',
     TIMESTAMP WITH TIME ZONE '2026-10-20 08:45:00+00', TIMESTAMP WITH TIME ZONE '2026-10-20 08:45:00+00'),
    ('EMP-000005', 'Yuki', 'Tanaka', 'yuki.tanaka@example.com', 'Frontend Engineer', 'Engineering', DATE '2026-11-09',
     'Daniel Okafor', 'yuki-tanaka', 'acme-corp/web-app', 'WRITE', 'hr', 'hr',
     TIMESTAMP WITH TIME ZONE '2026-10-22 11:20:00+00', TIMESTAMP WITH TIME ZONE '2026-10-22 11:20:00+00'),
    ('EMP-000006', 'Carlos', 'Mendes', 'carlos.mendes@example.com', 'QA Engineer', 'Quality', DATE '2026-11-16',
     'Priya Nair', 'cmendes', 'acme-corp/test-automation', 'WRITE', 'admin', 'admin',
     TIMESTAMP WITH TIME ZONE '2026-10-24 16:00:00+00', TIMESTAMP WITH TIME ZONE '2026-10-24 16:00:00+00');

-- Workflows -------------------------------------------------------------------------------------------------------

INSERT INTO onboarding_workflows (employee_id, overall_status, current_step, email_approval_required,
                                  started_at, completed_at, last_error, created_at, updated_at)
SELECT e.id, 'COMPLETED', 'COMPLETE_WORKFLOW', FALSE, e.created_at, e.created_at + INTERVAL '1' MINUTE, NULL,
       e.created_at, e.created_at + INTERVAL '1' MINUTE
FROM employees e
WHERE e.employee_number IN ('EMP-000001', 'EMP-000002', 'EMP-000003', 'EMP-000006');

INSERT INTO onboarding_workflows (employee_id, overall_status, current_step, email_approval_required,
                                  started_at, completed_at, last_error, created_at, updated_at)
SELECT e.id, 'FAILED', 'CREATE_GITHUB_ISSUE', FALSE, e.created_at, NULL,
       'GitHub denied access or rate limited the request (HTTP 403) while creating the issue. The token needs Issues read/write permission.',
       e.created_at, e.created_at + INTERVAL '1' MINUTE
FROM employees e
WHERE e.employee_number = 'EMP-000004';

INSERT INTO onboarding_workflows (employee_id, overall_status, current_step, email_approval_required,
                                  started_at, completed_at, last_error, created_at, updated_at)
SELECT e.id, 'AWAITING_APPROVAL', 'SEND_EMAIL', TRUE, e.created_at, NULL, NULL,
       e.created_at, e.created_at + INTERVAL '1' MINUTE
FROM employees e
WHERE e.employee_number = 'EMP-000005';

-- Steps -----------------------------------------------------------------------------------------------------------

-- Completed workflows: every step succeeded.
INSERT INTO workflow_steps (workflow_id, step_name, step_order, status, attempts, started_at, completed_at,
                            error_message, external_reference, created_at, updated_at)
SELECT w.id, s.step_name, s.step_order, 'SUCCESS', 1, w.started_at, w.started_at + INTERVAL '30' SECOND, NULL,
       CASE s.step_name
           WHEN 'SAVE_RECORD' THEN e.employee_number
           WHEN 'GENERATE_CONTENT' THEN 'source=AI'
           WHEN 'SEND_EMAIL' THEN '<sample-' || e.employee_number || '@onboarding.local>'
           WHEN 'CREATE_GITHUB_ISSUE' THEN '#' || CAST(40 + w.id AS VARCHAR(10))
       END,
       w.started_at, w.started_at + INTERVAL '30' SECOND
FROM onboarding_workflows w
JOIN employees e ON e.id = w.employee_id
CROSS JOIN (VALUES ('VALIDATE_DETAILS', 1), ('SAVE_RECORD', 2), ('GENERATE_CONTENT', 3),
                   ('SEND_EMAIL', 4), ('CREATE_GITHUB_ISSUE', 5), ('COMPLETE_WORKFLOW', 6)) AS s (step_name, step_order)
WHERE w.overall_status = 'COMPLETED';

-- Failed workflow: email sent, GitHub issue creation failed (retryable from the UI).
INSERT INTO workflow_steps (workflow_id, step_name, step_order, status, attempts, started_at, completed_at,
                            error_message, external_reference, created_at, updated_at)
SELECT w.id, s.step_name, s.step_order, s.status, s.attempts,
       CASE WHEN s.status = 'PENDING' THEN NULL ELSE w.started_at END,
       CASE WHEN s.status = 'PENDING' THEN NULL ELSE w.started_at + INTERVAL '30' SECOND END,
       CASE WHEN s.status = 'FAILED' THEN w.last_error END,
       CASE s.step_name
           WHEN 'SAVE_RECORD' THEN e.employee_number
           WHEN 'GENERATE_CONTENT' THEN 'source=AI'
           WHEN 'SEND_EMAIL' THEN '<sample-' || e.employee_number || '@onboarding.local>'
       END,
       w.started_at, w.started_at + INTERVAL '30' SECOND
FROM onboarding_workflows w
JOIN employees e ON e.id = w.employee_id
CROSS JOIN (VALUES ('VALIDATE_DETAILS', 1, 'SUCCESS', 1), ('SAVE_RECORD', 2, 'SUCCESS', 1),
                   ('GENERATE_CONTENT', 3, 'SUCCESS', 1), ('SEND_EMAIL', 4, 'SUCCESS', 1),
                   ('CREATE_GITHUB_ISSUE', 5, 'FAILED', 1), ('COMPLETE_WORKFLOW', 6, 'PENDING', 0))
    AS s (step_name, step_order, status, attempts)
WHERE w.overall_status = 'FAILED';

-- Workflow waiting for HR to approve the generated email.
INSERT INTO workflow_steps (workflow_id, step_name, step_order, status, attempts, started_at, completed_at,
                            error_message, external_reference, created_at, updated_at)
SELECT w.id, s.step_name, s.step_order, s.status, s.attempts,
       CASE WHEN s.status = 'PENDING' THEN NULL ELSE w.started_at END,
       CASE WHEN s.status IN ('PENDING', 'AWAITING_APPROVAL') THEN NULL ELSE w.started_at + INTERVAL '30' SECOND END,
       CASE WHEN s.status = 'AWAITING_APPROVAL' THEN 'Waiting for an HR user to review and approve the email.' END,
       CASE s.step_name
           WHEN 'SAVE_RECORD' THEN e.employee_number
           WHEN 'GENERATE_CONTENT' THEN 'source=AI'
       END,
       w.started_at, w.started_at + INTERVAL '30' SECOND
FROM onboarding_workflows w
JOIN employees e ON e.id = w.employee_id
CROSS JOIN (VALUES ('VALIDATE_DETAILS', 1, 'SUCCESS', 1), ('SAVE_RECORD', 2, 'SUCCESS', 1),
                   ('GENERATE_CONTENT', 3, 'SUCCESS', 1), ('SEND_EMAIL', 4, 'AWAITING_APPROVAL', 1),
                   ('CREATE_GITHUB_ISSUE', 5, 'PENDING', 0), ('COMPLETE_WORKFLOW', 6, 'PENDING', 0))
    AS s (step_name, step_order, status, attempts)
WHERE w.overall_status = 'AWAITING_APPROVAL';

-- Artifacts -------------------------------------------------------------------------------------------------------

INSERT INTO onboarding_artifacts (workflow_id, artifact_type, recipient, subject, content, metadata, content_source,
                                  external_url, external_id, status, sent_at, created_by, updated_by, created_at, updated_at)
SELECT w.id, 'WELCOME_EMAIL', e.email,
       'Welcome to Acme Corp, ' || e.first_name || '!',
       'Hi ' || e.first_name || ',

Welcome to Acme Corp! We are delighted that you are joining the ' || e.department || ' team as our new ' || e.job_title || '.

' || e.reporting_manager || ' will be your reporting manager and will help you settle in during your first weeks. The checklist below will help you track your onboarding progress.

If you have any questions before your first day, simply reply to this email.

The Acme Corp People Team',
       '{"checklist":["Complete HR paperwork and review the employee handbook","Set up laptop, email and single sign-on","Configure the ' || e.department || ' development environment","Meet your manager and onboarding buddy","Join the team channels and stand-ups","Review the team documentation"],"repository":"acme-corp/onboarding","requestedRepository":"' || e.github_repository || '"}',
       'AI', NULL,
       CASE WHEN w.overall_status = 'AWAITING_APPROVAL' THEN NULL ELSE '<sample-' || e.employee_number || '@onboarding.local>' END,
       CASE WHEN w.overall_status = 'AWAITING_APPROVAL' THEN 'DRAFT' ELSE 'SENT' END,
       CASE WHEN w.overall_status = 'AWAITING_APPROVAL' THEN NULL ELSE w.started_at + INTERVAL '30' SECOND END,
       'system', 'system', w.started_at, w.started_at + INTERVAL '30' SECOND
FROM onboarding_workflows w
JOIN employees e ON e.id = w.employee_id
WHERE e.employee_number LIKE 'EMP-00000%';

INSERT INTO onboarding_artifacts (workflow_id, artifact_type, recipient, subject, content, metadata, content_source,
                                  external_url, external_id, status, sent_at, created_by, updated_by, created_at, updated_at)
SELECT w.id, 'GITHUB_ISSUE', NULL,
       'Onboarding: ' || e.first_name || ' ' || e.last_name || ' (' || e.job_title || ')',
       '<!-- onboarding-workflow:' || CAST(w.id AS VARCHAR(20)) || ' -->

## Onboarding: ' || e.first_name || ' ' || e.last_name || '

| Field | Value |
|---|---|
| Employee ID | ' || e.employee_number || ' |
| Role | ' || e.job_title || ' |
| Department | ' || e.department || ' |
| Joining date | ' || CAST(e.joining_date AS VARCHAR(10)) || ' |
| Reporting manager | ' || e.reporting_manager || ' |
| GitHub username | `' || e.github_username || '` |
| Requested repository | `' || e.github_repository || '` |
| Requested access level | ' || e.access_level || ' |

### Onboarding tasks

- [ ] Repository access: grant `' || e.access_level || '` on `' || e.github_repository || '` after approval by a repository administrator
- [ ] Development environment setup
- [ ] Team introductions
- [ ] Documentation review

> **Note:** This issue does not grant any access. Repository permissions must be provisioned separately with explicit authorization and audit logging.
',
       '{"checklist":["Complete HR paperwork and review the employee handbook","Set up laptop, email and single sign-on","Configure the ' || e.department || ' development environment","Meet your manager and onboarding buddy","Join the team channels and stand-ups","Review the team documentation"],"repository":"acme-corp/onboarding","requestedRepository":"' || e.github_repository || '"}',
       'AI',
       CASE WHEN w.overall_status = 'COMPLETED' THEN 'https://github.com/acme-corp/onboarding/issues/' || CAST(40 + w.id AS VARCHAR(10)) END,
       CASE WHEN w.overall_status = 'COMPLETED' THEN CAST(40 + w.id AS VARCHAR(10)) END,
       CASE w.overall_status WHEN 'COMPLETED' THEN 'CREATED' WHEN 'FAILED' THEN 'FAILED' ELSE 'DRAFT' END,
       NULL, 'system', 'system', w.started_at, w.started_at + INTERVAL '30' SECOND
FROM onboarding_workflows w
JOIN employees e ON e.id = w.employee_id
WHERE e.employee_number LIKE 'EMP-00000%';

-- Audit trail -----------------------------------------------------------------------------------------------------

INSERT INTO audit_events (actor, action, entity_type, entity_id, details, correlation_id, created_at)
SELECT e.created_by, 'EMPLOYEE_CREATED', 'WORKFLOW', w.id,
       'Employee ' || e.employee_number || ' submitted for onboarding', NULL, w.started_at
FROM onboarding_workflows w
JOIN employees e ON e.id = w.employee_id
WHERE e.employee_number LIKE 'EMP-00000%';
