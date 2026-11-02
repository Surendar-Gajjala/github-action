/* eslint-disable @typescript-eslint/no-explicit-any */
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { definitionsApi } from '../../api/endpoints';
import type { ApprovalRules, ValidationResult } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime } from '../../components/format';
import { EmptyState, ErrorState, InlineError, LoadingState } from '../../components/States';
import { StatusBadge } from '../../components/StatusBadge';
import { useToast } from '../../components/Toast';
import { BpmnCanvas, type BpmnInstance } from './bpmn/BpmnCanvas';
import { PropertiesPanel } from './bpmn/PropertiesPanel';
import { RulesEditor } from './RulesEditor';

export function DesignerSection({ workflowKey }: { workflowKey?: string }) {
  const navigate = useNavigate();
  const definitions = useQuery({ queryKey: ['definitions'], queryFn: definitionsApi.list });

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <label className="label mb-0" htmlFor="designer-def">Workflow</label>
        <select id="designer-def" className="input w-96" value={workflowKey ?? ''}
          onChange={(e) => navigate(`/workflow/designer/${e.target.value}`)}>
          <option value="" disabled>Select a workflow definition…</option>
          {(definitions.data ?? []).map((d) => <option key={d.key} value={d.key}>{d.name} ({d.key})</option>)}
        </select>
      </div>
      {!workflowKey ? (
        <div className="card"><EmptyState title="Select a workflow to design" hint="Or create a new workflow in the Definitions section." /></div>
      ) : (
        <Designer key={workflowKey} workflowKey={workflowKey} />
      )}
    </div>
  );
}

function Designer({ workflowKey }: { workflowKey: string }) {
  const [params] = useSearchParams();
  const versionParam = params.get('version');
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const qc = useQueryClient();
  const toast = useToast();

  const detail = useQuery({ queryKey: ['definition', workflowKey], queryFn: () => definitionsApi.get(workflowKey) });
  const versionView = useQuery({
    queryKey: ['definition', workflowKey, 'version', versionParam],
    queryFn: () => definitionsApi.version(workflowKey, Number(versionParam)),
    enabled: !!versionParam,
  });
  const workers = useQuery({ queryKey: ['workers'], queryFn: definitionsApi.workers });

  const source = versionParam ? versionView.data : detail.data?.current;
  const [editMode, setEditMode] = useState(false);
  const [modeler, setModeler] = useState<BpmnInstance | null>(null);
  const [selected, setSelected] = useState<any>(null);
  const [rules, setRules] = useState<ApprovalRules | null>(null);
  const [dirty, setDirty] = useState(false);
  const [validation, setValidation] = useState<ValidationResult | null>(null);
  const [xml, setXml] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);

  const isDraft = source?.meta.status === 'DRAFT';
  const readOnly = !isAdmin || !!versionParam || (!isDraft && !editMode);

  useEffect(() => {
    if (source) {
      setXml(source.bpmnXml);
      setRules(source.rules);
      setDirty(false);
      setValidation(null);
      setSelected(null);
    }
  }, [source]);

  async function currentXml(): Promise<string> {
    if (!modeler) return xml;
    const { xml: out } = await modeler.saveXML({ format: true });
    return out ?? xml;
  }

  const save = useMutation({
    mutationFn: async () => definitionsApi.saveDraft(workflowKey, { bpmnXml: await currentXml(), rules: rules! }),
    onSuccess: (d) => {
      qc.setQueryData(['definition', workflowKey], d);
      void qc.invalidateQueries({ queryKey: ['definitions'] });
      setDirty(false);
      setEditMode(false);
      toast.success(`Draft v${d.current?.meta.versionNumber} saved`);
    },
  });

  const validate = useMutation({
    mutationFn: async () => definitionsApi.validate(await currentXml(), workflowKey),
    onSuccess: setValidation,
  });

  const publish = useMutation({
    mutationFn: async () => {
      if (dirty) await definitionsApi.saveDraft(workflowKey, { bpmnXml: await currentXml(), rules: rules! });
      return definitionsApi.publish(workflowKey);
    },
    onSuccess: (d) => {
      qc.setQueryData(['definition', workflowKey], d);
      void qc.invalidateQueries({ queryKey: ['definitions'] });
      setDirty(false);
      setValidation(null);
      toast.success(`${workflowKey} v${d.definition.publishedVersion} published and deployed`);
    },
  });

  async function download() {
    const blob = new Blob([await currentXml()], { type: 'application/xml' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `${workflowKey}.bpmn`;
    a.click();
    URL.revokeObjectURL(a.href);
  }

  function importFile(file: File) {
    file.text().then((text) => {
      setXml(text);
      setEditMode(true);
      setDirty(true);
    });
  }

  if (detail.isLoading || (versionParam && versionView.isLoading)) return <LoadingState />;
  if (detail.error) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />;
  if (!source || !rules) return <EmptyState title="This definition has no versions" />;

  const def = detail.data!.definition;
  return (
    <div className="space-y-3">
      <div className="card flex flex-wrap items-center gap-3 p-3">
        <div className="mr-auto">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-slate-900">{def.name}</span>
            <StatusBadge status={source.meta.status} label={`v${source.meta.versionNumber} ${source.meta.status.toLowerCase()}`} />
            {!def.active && <StatusBadge status="INACTIVE" />}
            {dirty && <span className="text-xs font-medium text-amber-700">● unsaved changes</span>}
          </div>
          <p className="text-xs text-slate-500">
            {def.key} · {def.type} · updated {fmtDateTime(source.meta.updatedAt ?? source.meta.createdAt)}
            {source.meta.publishedAt && <> · published {fmtDateTime(source.meta.publishedAt)} by {source.meta.publishedBy}</>}
            {source.meta.engineProcessDefinitionKey && <> · engine key {source.meta.engineProcessDefinitionKey}</>}
          </p>
        </div>
        {versionParam && <span className="text-xs text-slate-500">Viewing historical version (read-only)</span>}
        {isAdmin && !versionParam && (
          <>
            {!isDraft && !editMode && (
              <button className="btn-secondary" onClick={() => setEditMode(true)}>Edit (new draft v{(def.latestVersion ?? 0) + 1})</button>
            )}
            {!readOnly && (
              <>
                <button className="btn-secondary" onClick={() => fileInput.current?.click()}>Import .bpmn</button>
                <input ref={fileInput} type="file" accept=".bpmn,.xml" hidden onChange={(e) => e.target.files?.[0] && importFile(e.target.files[0])} />
                <button className="btn-secondary" disabled={validate.isPending} onClick={() => validate.mutate()}>Validate</button>
                <button className="btn-secondary" disabled={save.isPending || (!dirty && isDraft)} onClick={() => save.mutate()}>
                  {save.isPending ? 'Saving…' : 'Save draft'}
                </button>
              </>
            )}
            {(isDraft || editMode) && (
              <button className="btn-primary" disabled={publish.isPending || (!isDraft && !dirty)} onClick={() => publish.mutate()}>
                {publish.isPending ? 'Publishing…' : 'Publish'}
              </button>
            )}
          </>
        )}
        <button className="btn-ghost" onClick={() => void download()}>Download XML</button>
      </div>

      <InlineError error={save.error || publish.error || validate.error} />
      {validation && (
        <div className={`rounded-md border p-3 ${validation.valid ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-rose-300 bg-rose-50 text-rose-800'}`}>
          <p className="font-medium">{validation.valid ? 'Definition is valid' : 'Definition has errors'}</p>
          {validation.errors.map((e) => <p key={e}>• {e}</p>)}
          {validation.warnings.map((w) => <p key={w} className="text-amber-800">⚠ {w}</p>)}
        </div>
      )}

      <div className="grid gap-3 lg:grid-cols-[1fr_320px]">
        <div className="card overflow-hidden">
          <BpmnCanvas
            xml={xml}
            readOnly={readOnly}
            onReady={(m) => { setModeler(m); setSelected(null); }}
            onSelect={setSelected}
            onChange={() => setDirty(true)}
          />
        </div>
        <div className="card max-h-[560px] overflow-y-auto">
          <div className="border-b border-slate-200 px-4 py-2 font-semibold text-slate-900">Properties</div>
          <PropertiesPanel modeler={modeler} element={selected} workers={workers.data ?? []} readOnly={readOnly} />
        </div>
      </div>

      <div className="card">
        <div className="border-b border-slate-200 px-4 py-2 font-semibold text-slate-900">Approval rules</div>
        <RulesEditor rules={rules} readOnly={readOnly} onChange={(r) => { setRules(r); setDirty(true); }} />
      </div>
    </div>
  );
}
