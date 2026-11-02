import { useCallback, useState } from 'react';
import { api } from '../api.js';
import { CadEditor, CadPreview, ErrorBox, Stepper, useCadJson } from '../components/shared.jsx';
import DataFlow from '../components/DataFlow.jsx';

const STEPS = [
  { id: 'cad', title: '1. CAD JSON', sub: 'export from CAD' },
  { id: 'documents', title: '2. Create documents', sub: 'createDocuments → DIRs' },
  { id: 'checkin', title: '3. Check in', sub: 'save(SAVECHECKIN) → originals, BoD' },
  { id: 'materials', title: '4. Create materials', sub: 'materialCreate → material + link' },
  { id: 'bom', title: '5. Maintain BOM', sub: 'cdeskBomWizard → material BOM' },
];

const NEXT = {
  checkin: { label: 'Check in →', call: api.ectr.checkIn },
  materials: { label: 'Create materials →', call: api.ectr.createMaterials },
  bom: { label: 'Maintain BOM →', call: api.ectr.maintainBom },
};

/** CAD → SAP ECTR → SAP backend (simulated ECTR steps, mock SAP backend). */
export default function CadToPlmFlow({ onLog }) {
  const [session, setSession] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const reset = useCallback(() => { setSession(null); setError(null); }, []);
  const cad = useCadJson(reset);

  async function run(fn) {
    setBusy(true);
    setError(null);
    const r = await fn();
    if (r.ok) setSession(r.data); else setError(r);
    setBusy(false);
  }

  const status = session?.status;
  const order = ['DOCUMENTS_CREATED', 'CHECKED_IN', 'MATERIALS_CREATED', 'BOM_MAINTAINED'];
  const reached = (s) => status && order.indexOf(status) >= order.indexOf(s);
  const failedStep = error && (session ? session.nextStep && { checkin: 'checkin', materials: 'materials', bom: 'bom' }[session.nextStep] : 'documents');
  const state = {
    cad: cad.parsed.data ? 'done' : 'fail',
    documents: reached('DOCUMENTS_CREATED') ? 'done' : failedStep === 'documents' ? 'fail' : '',
    checkin: reached('CHECKED_IN') ? 'done' : failedStep === 'checkin' ? 'fail' : '',
    materials: reached('MATERIALS_CREATED') ? 'done' : failedStep === 'materials' ? 'fail' : '',
    bom: reached('BOM_MAINTAINED') ? 'done' : failedStep === 'bom' ? 'fail' : '',
  };
  const next = session?.nextStep && NEXT[session.nextStep];
  const flow = dataFlow(cad.parsed.data, session, reached, error);

  return (
    <>
      <p className="hint lead">
        <b>CAD → SAP ECTR → SAP backend.</b> The designer works in CAD; SAP ECTR on the same PC writes directly into the
        PLM functions <i>inside</i> SAP ECC / S/4HANA. There is no external PLM. Real ECTR is a desktop client driven by the
        client-side ECTR Connector, <b>not a REST API</b>. Here its four steps are <b>simulated</b> on the server so you can watch them.
      </p>

      <DataFlow title="Data flow: CAD to PLM (inside SAP)" nodes={flow.nodes} edges={flow.edges} />

      <Stepper steps={STEPS} state={state} />

      <div className="grid">
        <CadEditor cad={cad} />

        <section className="card">
          <h2>CAD structure <span className="muted">(what the designer modelled)</span></h2>
          {cad.parsed.data ? <CadPreview cad={cad.parsed.data} /> : <p className="muted">No valid JSON yet.</p>}
          <div className="actions">
            <button className="primary" disabled={!cad.parsed.data || busy}
                    onClick={() => { reset(); run(() => api.ectr.createDocuments(cad.text, onLog)); }}>
              {busy && !session ? 'Starting…' : 'Start ECTR session: create documents →'}
            </button>
          </div>
          {error && !session && <ErrorBox res={error} />}
        </section>

        <section className="card full">
          <div className="row between">
            <h2>SAP backend <span className="muted">(MOCK, ECC / S/4HANA PLM functions)</span></h2>
            {session && (
              <span className="muted small">
                Session <code>{session.sessionId.slice(0, 8)}…</code> · status <b>{session.status}</b> ·
                totals in mock SAP: {session.sapBackendTotals.documents} DIRs, {session.sapBackendTotals.materials} materials, {session.sapBackendTotals.boms} BOMs
              </span>
            )}
          </div>

          {!session && <p className="muted">Start an ECTR session to create the documents in SAP.</p>}

          {session && (
            <>
              <div className="row gap">
                {next ? (
                  <button className="primary" disabled={busy} onClick={() => run(() => next.call(session.sessionId, onLog))}>
                    {busy ? 'Working…' : next.label}
                  </button>
                ) : (
                  <span className="pill ok">All ECTR steps done</span>
                )}
                <button disabled={busy} onClick={() => run(() => api.ectr.get(session.sessionId, onLog))}>Reload session</button>
              </div>
              {error && <ErrorBox res={error} />}

              <h3>Document Info Records (DIR)</h3>
              <div className="scroll">
                <table>
                  <thead><tr><th>DIR key</th><th>Part</th><th>CAD file → SAP file</th><th>Status</th><th>Original</th><th>Material link</th><th></th></tr></thead>
                  <tbody>
                    {session.documents.map((d) => (
                      <tr key={d.dirKey} className={!d.bomRelevant ? 'soft' : ''}>
                        <td><code>{d.dirKey}</code></td>
                        <td><b>{d.partNumber}</b> <span className="muted">rev {d.cadRevision}</span></td>
                        <td className="small">{d.cadFileName} → <code>{d.sapFileName}</code></td>
                        <td><span className={'pill ' + (d.status === 'CHECKED_IN' ? 'ok' : 'warn')}>{d.status}</span></td>
                        <td><code>{d.originalId || '–'}</code></td>
                        <td>{d.linkedMaterial || '–'}</td>
                        <td className="small">
                          {d.assembly && <span className="pill info">assembly</span>}{' '}
                          {!d.bomRelevant && <span className="pill neutral">document only</span>}{' '}
                          {d.reused && <span className="pill neutral">reused</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="two">
                <div>
                  <h3>Document structure (BoD)</h3>
                  {session.documentStructure.length === 0 ? (
                    <p className="muted">Written on check-in.</p>
                  ) : (
                    <table>
                      <thead><tr><th>Child DIR</th><th>Part</th><th className="num">Qty</th><th>In BOM?</th></tr></thead>
                      <tbody>
                        {session.documentStructure.map((s) => (
                          <tr key={s.childDirKey} className={!s.bomRelevant ? 'soft' : ''}>
                            <td><code>{s.childDirKey}</code></td><td>{s.partNumber}</td>
                            <td className="num">{s.quantity}</td><td>{s.bomRelevant ? 'yes' : 'no'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                  <p className="small muted">The BoD mirrors the CAD assembly, including reference geometry.</p>
                </div>
                <div>
                  <h3>Material BOM</h3>
                  {!session.bom ? (
                    <p className="muted">Written by the BOM step.</p>
                  ) : (
                    <>
                      <p className="small">
                        <b>{session.bom.materialNumber}</b> plant {session.bom.plant}
                        {session.bom.changeNumber && <> · change {session.bom.changeNumber}</>} ·{' '}
                        <span className="pill info">{session.bom.created ? 'created' : 'updated'}</span>
                      </p>
                      <table>
                        <thead><tr><th>Item</th><th>Component</th><th className="num">Qty</th><th>Unit</th></tr></thead>
                        <tbody>
                          {session.bom.items.map((i) => (
                            <tr key={i.itemNumber}><td>{i.itemNumber}</td><td>{i.componentMaterial}</td><td className="num">{i.quantity}</td><td>{i.unit}</td></tr>
                          ))}
                        </tbody>
                      </table>
                      <p className="hint">{session.bom.note}</p>
                    </>
                  )}
                </div>
              </div>

              <h3>Materials</h3>
              {session.materials.length === 0 ? <p className="muted">Created by the materials step.</p> : (
                <table>
                  <thead><tr><th>Material</th><th>Description</th><th>Linked DIR</th><th></th></tr></thead>
                  <tbody>
                    {session.materials.map((m) => (
                      <tr key={m.materialNumber}>
                        <td><b>{m.materialNumber}</b></td><td>{m.description || '–'}</td><td><code>{m.linkedDirKey}</code></td>
                        <td><span className={'pill ' + (m.created ? 'ok' : 'neutral')}>{m.created ? 'created' : 'already existed'}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </section>

        {session && (
          <section className="card full">
            <h2>What ECTR did <span className="muted">(simulated ECTR Connector calls)</span></h2>
            <ol className="steps">
              {session.steps.map((s) => (
                <li key={s.step}>
                  <b>{s.step}</b> <code>{s.connectorCall}</code>
                  <ul className="notes">{s.messages.map((m, i) => <li key={i}>{m}</li>)}</ul>
                </li>
              ))}
            </ol>
            {session.notes.length > 0 && (
              <>
                <h3>CAD analysis notes</h3>
                <ul className="notes">{session.notes.map((n) => <li key={n}>{n}</li>)}</ul>
              </>
            )}
          </section>
        )}
      </div>
    </>
  );
}

/** Builds the data-flow view (CAD -> ECTR -> SAP backend) from the CAD JSON and the ECTR session. */
function dataFlow(cadData, session, reached, error) {
  const a = cadData?.assembly;
  const comps = Array.isArray(a?.components) ? a.components : [];
  const docs = session?.documents ?? [];
  const checkedIn = docs.filter((d) => d.status === 'CHECKED_IN');
  const done = (s) => (reached(s) ? 'done' : '');

  const nodes = [
    {
      id: 'cad', system: 'CAD system', role: 'designer models the assembly',
      state: a ? 'done' : 'fail',
      lines: a ? [
        `${a.partNumber} rev ${a.revision} (${cadData.source?.fileName ?? 'assembly'})`,
        `${comps.length} instances, ${comps.filter((c) => c.excludeFromBom).length} not for BOM`,
        `exported by ${cadData.source?.exportedBy ?? '–'}`,
      ] : [],
    },
    {
      id: 'ectr', system: 'SAP ECTR', role: 'desktop client, Connector calls', mock: true,
      state: error ? 'fail' : reached('BOM_MAINTAINED') ? 'done' : session ? 'active' : '',
      lines: session ? [
        `session ${session.sessionId.slice(0, 8)}…`,
        `status ${session.status}`,
        ...session.steps.map((s) => '✓ ' + s.connectorCall.split('(')[0]),
      ] : [],
    },
    {
      id: 'sap', system: 'SAP ECC / S/4HANA', role: 'PLM functions: DMS, material, BOM', mock: true,
      state: reached('BOM_MAINTAINED') ? 'done' : session ? 'active' : '',
      lines: session ? [
        `${docs.length} DIRs (${checkedIn.length} checked in)`,
        `${checkedIn.filter((d) => d.originalId).length} originals on content repository`,
        `BoD: ${session.documentStructure.length} child documents`,
        `${session.materials.length} materials linked to DIRs`,
        session.bom ? `BOM ${session.bom.materialNumber}/${session.bom.plant}: ${session.bom.items.length} items` : 'BOM: not yet',
      ] : [],
    },
  ];

  const edges = [
    {
      label: 'CAD files + metadata',
      state: session ? 'done' : a ? 'active' : '',
      payload: a ? [
        `${a.partNumber} + ${new Set(comps.map((c) => c.partNumber)).size} parts`,
        ...[...new Set(comps.map((c) => c.fileName).filter(Boolean))].slice(0, 3),
      ] : [],
    },
    {
      label: 'RFC (where configured)',
      state: done('BOM_MAINTAINED') || (session ? 'active' : ''),
      payload: session ? session.steps.map((s) => s.step.replace(/^\d+\.\s*/, '')) : [],
    },
  ];
  return { nodes, edges };
}
