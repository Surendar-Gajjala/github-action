import type { ApprovalRules, ManagerApproval } from '../../api/types';

const MANAGER_OPTIONS: { value: ManagerApproval; label: string }[] = [
  { value: 'ALWAYS', label: 'Always' },
  { value: 'NEVER', label: 'Never' },
  { value: 'WHEN_PARTS_REMOVED', label: 'When parts are removed' },
  { value: 'WHEN_PREVIOUSLY_APPROVED', label: 'For changes to approved items' },
  { value: 'WHEN_PART_CHANGES_EXCEED', label: 'When part changes exceed a threshold' },
];
const ROLES = ['ENGINEERING', 'MANAGER'];

/**
 * Approval requirements stored with a definition version. The backend evaluates managerApproval when a workflow
 * starts (process variable managerApprovalRequired) and re-checks requiredRoles before releasing a revision.
 */
export function RulesEditor({ rules, onChange, readOnly }: { rules: ApprovalRules; onChange: (r: ApprovalRules) => void; readOnly?: boolean }) {
  const set = (patch: Partial<ApprovalRules>) => onChange({ ...rules, ...patch });
  return (
    <div className="grid gap-4 p-4 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <label className="label" htmlFor="manager-approval">Manager approval required</label>
        <select id="manager-approval" className="input" disabled={readOnly} value={rules.managerApproval}
          onChange={(e) => set({ managerApproval: e.target.value as ManagerApproval })}>
          {MANAGER_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <p className="mt-1 text-xs text-slate-500">Sets process variable <code>managerApprovalRequired</code>.</p>
      </div>
      <div>
        <label className="label" htmlFor="threshold">Part-change threshold</label>
        <input id="threshold" type="number" min={0} className="input w-28"
          disabled={readOnly || rules.managerApproval !== 'WHEN_PART_CHANGES_EXCEED'}
          value={rules.partChangeThreshold}
          onChange={(e) => set({ partChangeThreshold: Math.max(0, Number(e.target.value)) })} />
        <p className="mt-1 text-xs text-slate-500">Added + removed parts above this need a manager.</p>
      </div>
      <div>
        <p className="label">Required approvals before release</p>
        <div className="flex gap-4">
          {ROLES.map((r) => (
            <label key={r} className="flex items-center gap-1.5">
              <input type="checkbox" disabled={readOnly} checked={rules.requiredRoles.includes(r)}
                onChange={(e) => set({ requiredRoles: e.target.checked ? [...rules.requiredRoles, r] : rules.requiredRoles.filter((x) => x !== r) })} />
              {r}
            </label>
          ))}
        </div>
        <p className="mt-1 text-xs text-slate-500">Verified by the release worker.</p>
      </div>
      <div className="space-y-2">
        <div>
          <label className="label" htmlFor="due-days">Task due in (days)</label>
          <input id="due-days" type="number" min={1} max={365} className="input w-28" disabled={readOnly}
            value={rules.dueInDays ?? ''} onChange={(e) => set({ dueInDays: e.target.value ? Number(e.target.value) : null })} />
        </div>
        <label className="flex items-center gap-1.5">
          <input type="checkbox" disabled={readOnly} checked={rules.preventSelfApproval}
            onChange={(e) => set({ preventSelfApproval: e.target.checked })} />
          Submitter cannot approve
        </label>
      </div>
    </div>
  );
}
