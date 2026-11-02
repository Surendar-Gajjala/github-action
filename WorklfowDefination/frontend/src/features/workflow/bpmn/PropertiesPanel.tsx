/* eslint-disable @typescript-eslint/no-explicit-any */
import { useEffect, useReducer } from 'react';
import type { WorkerInfo } from '../../../api/types';
import type { BpmnInstance } from './BpmnCanvas';

const CANDIDATE_GROUPS = ['ENGINEERING', 'MANAGER'];
const CONDITION_PRESETS = [
  { label: 'Approved', expr: '= lastDecision = "APPROVED"' },
  { label: 'Rejected', expr: '= lastDecision = "REJECTED"' },
  { label: 'Validation passed', expr: '= validationPassed = true' },
  { label: 'Manager required', expr: '= managerApprovalRequired = true' },
];

interface Props {
  modeler: BpmnInstance | null;
  element: any | null;
  workers: WorkerInfo[];
  readOnly?: boolean;
}

/**
 * Minimal properties panel for the elements this application executes: names, service-task job types (workers),
 * approval-task candidate groups, gateway conditions and default flows. Edits go through bpmn-js modeling commands,
 * so they are undoable and serialized as standard BPMN + zeebe extensions.
 */
export function PropertiesPanel({ modeler, element, workers, readOnly }: Props) {
  const [, rerender] = useReducer((x: number) => x + 1, 0);
  useEffect(() => {
    if (!modeler) return;
    const bus: any = modeler.get('eventBus');
    const handler = () => rerender();
    bus.on('elements.changed', handler);
    return () => bus.off('elements.changed', handler);
  }, [modeler]);

  if (!modeler || !element) {
    return (
      <div className="p-4 text-slate-500">
        Select an element in the diagram to configure it. Use the palette on the left to add tasks, gateways and events;
        use the wrench icon on an element to change its type (e.g. task → user task).
      </div>
    );
  }

  const bo = element.businessObject;
  const type: string = bo.$type;
  const modeling: any = readOnly ? null : (modeler as any).get('modeling');
  const moddle: any = modeler.get('moddle');
  const registry: any = modeler.get('elementRegistry');

  const ext = (t: string) => (bo.extensionElements?.get('values') ?? []).find((v: any) => v.$type === t);

  function setExtension(t: string, attrs: Record<string, unknown>) {
    const values = (bo.extensionElements?.get('values') ?? []).filter((v: any) => v.$type !== t);
    const entry = moddle.create(t, attrs);
    const extensionElements = moddle.create('bpmn:ExtensionElements', { values: [...values, entry] });
    entry.$parent = extensionElements;
    modeling.updateProperties(element, { extensionElements });
  }

  const isServiceTask = type === 'bpmn:ServiceTask';
  const isUserTask = type === 'bpmn:UserTask';
  const isFlow = type === 'bpmn:SequenceFlow';
  const isGateway = type === 'bpmn:ExclusiveGateway';
  const source = isFlow ? bo.sourceRef : null;
  const fromGateway = source?.$type === 'bpmn:ExclusiveGateway';
  const isDefault = fromGateway && source.default === bo;
  const supported = [
    'bpmn:StartEvent', 'bpmn:EndEvent', 'bpmn:ServiceTask', 'bpmn:UserTask', 'bpmn:ExclusiveGateway', 'bpmn:SequenceFlow', 'bpmn:Process',
  ].includes(type);

  const taskDef = ext('zeebe:TaskDefinition');
  const assignment = ext('zeebe:AssignmentDefinition');
  const camundaUserTask = ext('zeebe:UserTask');

  return (
    <div className="space-y-4 p-4">
      <div>
        <p className="label">Element</p>
        <p className="font-medium text-slate-900">{type.replace('bpmn:', '')}</p>
        <p className="text-xs text-slate-500">id: {bo.id}</p>
        {!supported && (
          <p className="mt-2 rounded bg-amber-50 px-2 py-1 text-xs text-amber-800">
            This element type is not supported by the development workflow adapter. Camunda 8 can execute it, but publishing
            fails validation while the dev adapter is active.
          </p>
        )}
      </div>

      {type !== 'bpmn:Process' && (
        <div>
          <label className="label" htmlFor="el-name">Name</label>
          <input
            id="el-name"
            className="input"
            disabled={readOnly}
            value={bo.name ?? ''}
            onChange={(e) => modeling.updateLabel(element, e.target.value)}
          />
        </div>
      )}

      {isServiceTask && (
        <>
          <div>
            <label className="label" htmlFor="job-type">Worker (job type)</label>
            <select
              id="job-type"
              className="input"
              disabled={readOnly}
              value={taskDef?.type ?? ''}
              onChange={(e) => setExtension('zeebe:TaskDefinition', { type: e.target.value, retries: taskDef?.retries ?? '3' })}
            >
              <option value="">— select a worker —</option>
              {workers.map((w) => <option key={w.jobType} value={w.jobType}>{w.jobType}</option>)}
            </select>
            <p className="mt-1 text-xs text-slate-500">{workers.find((w) => w.jobType === taskDef?.type)?.description}</p>
          </div>
          <div>
            <label className="label" htmlFor="retries">Retries</label>
            <input
              id="retries"
              type="number"
              min={1}
              max={10}
              className="input w-24"
              disabled={readOnly || !taskDef}
              value={taskDef?.retries ?? '3'}
              onChange={(e) => setExtension('zeebe:TaskDefinition', { type: taskDef?.type, retries: e.target.value })}
            />
          </div>
        </>
      )}

      {isUserTask && (
        <div>
          <label className="label" htmlFor="candidate-group">Approver role (candidate group)</label>
          <select
            id="candidate-group"
            className="input"
            disabled={readOnly}
            value={assignment?.candidateGroups ?? ''}
            onChange={(e) => setExtension('zeebe:AssignmentDefinition', { candidateGroups: e.target.value })}
          >
            <option value="">— select a role —</option>
            {CANDIDATE_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
          </select>
          <p className="mt-1 text-xs text-slate-500">
            Completing the task sets <code>lastDecision</code> to APPROVED or REJECTED. Follow it with an exclusive gateway.
          </p>
          {camundaUserTask && (
            <p className="mt-2 rounded bg-rose-50 px-2 py-1 text-xs text-rose-800">
              Uses the Camunda user task implementation; approval tasks must use the job-worker implementation.
            </p>
          )}
        </div>
      )}

      {isGateway && (
        <div className="text-slate-600">
          <p className="label">Outgoing flows</p>
          <ul className="space-y-1 text-xs">
            {(bo.outgoing ?? []).map((f: any) => (
              <li key={f.id} className="rounded bg-slate-50 px-2 py-1">
                <b>{f.name || f.id}</b> → {f.targetRef?.name || f.targetRef?.id}
                <br />
                {bo.default === f ? <i>default flow</i> : <code>{f.conditionExpression?.body ?? 'no condition'}</code>}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs">Select a flow to set its condition.</p>
        </div>
      )}

      {isFlow && fromGateway && (
        <div className="space-y-2">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              disabled={readOnly}
              checked={isDefault}
              onChange={(e) => {
                const gateway = registry.get(source.id);
                modeling.updateProperties(gateway, { default: e.target.checked ? bo : undefined });
                if (e.target.checked) modeling.updateProperties(element, { conditionExpression: undefined });
              }}
            />
            Default flow (taken when no condition matches)
          </label>
          {!isDefault && (
            <>
              <label className="label" htmlFor="condition">Condition (FEEL)</label>
              <input
                id="condition"
                className="input font-mono text-xs"
                disabled={readOnly}
                placeholder='= lastDecision = "APPROVED"'
                value={bo.conditionExpression?.body ?? ''}
                onChange={(e) =>
                  modeling.updateProperties(element, {
                    conditionExpression: e.target.value
                      ? moddle.create('bpmn:FormalExpression', { body: e.target.value })
                      : undefined,
                  })
                }
              />
              {!readOnly && (
                <div className="flex flex-wrap gap-1">
                  {CONDITION_PRESETS.map((p) => (
                    <button
                      key={p.label}
                      type="button"
                      className="btn-secondary btn-sm"
                      onClick={() =>
                        modeling.updateProperties(element, {
                          conditionExpression: moddle.create('bpmn:FormalExpression', { body: p.expr }),
                        })
                      }
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              )}
              <p className="text-xs text-slate-500">
                Variables: <code>lastDecision</code>, <code>validationPassed</code>, <code>managerApprovalRequired</code>,{' '}
                <code>itemNumber</code>, <code>revision</code>.
              </p>
            </>
          )}
        </div>
      )}
    </div>
  );
}
