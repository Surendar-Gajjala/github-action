import { useCallback, useState } from 'react';
import { api } from '../api.js';
import { CadEditor, CadPreview, ErrorBox, Stepper, useCadJson } from '../components/shared.jsx';
import DataFlow from '../components/DataFlow.jsx';

const STEPS = [
  { id: 'import', title: '1. Engineering data in PLM', sub: 'CAD data → PLM BOM (IN_WORK)' },
  { id: 'release', title: '2. Release in PLM', sub: 'IN_WORK → RELEASED' },
  { id: 'transfer', title: '3. Integration layer', sub: 'validate, map, send to SAP' },
  { id: 'status', title: '4. Transfer status', sub: 'SAP response recorded' },
];

const newKey = () => 'react-' + Date.now().toString(36);

/** External PLM → integration layer → SAP ERP / S/4HANA (mock PLM, mock SAP gateway). */
export default function ErpToPlmFlow({ onLog, gatewayMode }) {
  const [importRes, setImportRes] = useState(null);
  const [bom, setBom] = useState(null);
  const [releaseRes, setReleaseRes] = useState(null);
  const [transferRes, setTransferRes] = useState(null);
  const [statusRes, setStatusRes] = useState(null);
  const [idemKey, setIdemKey] = useState(newKey);
  const [busy, setBusy] = useState(null);

  const resetAll = useCallback(() => {
    setImportRes(null); setBom(null); setReleaseRes(null);
    setTransferRes(null); setStatusRes(null); setIdemKey(newKey());
  }, []);
  const cad = useCadJson(resetAll);

  const bomKey = bom && { materialNumber: bom.materialNumber, revision: bom.revision, plant: bom.plant };

  async function doImport() {
    resetAll();
    setBusy('import');
    const r = await api.erp.importToPlm(cad.text, onLog);
    setImportRes(r);
    if (r.ok) setBom(r.data.bom);
    setBusy(null);
  }

  async function doRelease() {
    setBusy('release');
    const r = await api.erp.release(bomKey, onLog);
    setReleaseRes(r);
    if (r.ok) setBom(r.data);
    setBusy(null);
  }

  async function doTransfer(sameKey) {
    setBusy('transfer');
    const key = sameKey ? idemKey : newKey();
    setIdemKey(key);
    setStatusRes(null);
    setTransferRes(await api.erp.transfer(bomKey, key, onLog));
    setBusy(null);
  }

  async function doStatus() {
    setBusy('status');
    setStatusRes(await api.erp.transferStatus(transferRes.data.transferId, onLog));
    setBusy(null);
  }

  async function refreshBom() {
    const r = await api.erp.getBom(bomKey, onLog);
    if (r.ok) setBom(r.data);
  }

  const state = {
    import: importRes ? (importRes.ok ? 'done' : 'fail') : '',
    release: bom?.releaseState === 'RELEASED' ? 'done' : releaseRes && !releaseRes.ok ? 'fail' : '',
    transfer: transferRes ? (transferRes.data?.status === 'SUCCEEDED' ? 'done' : 'fail') : '',
    status: statusRes ? (statusRes.ok ? 'done' : 'fail') : '',
  };
  const flow = dataFlow({ cadData: cad.parsed.data, importRes, bom, transferRes, statusRes, idemKey, gatewayMode });

  return (
    <>
      <p className="hint lead">
        <b>External PLM → integration layer → SAP ERP / S/4HANA.</b> An external PLM (Teamcenter, Windchill, …) owns the
        engineering data and the release decision. An integration layer validates the <b>released</b> BOM, maps it and sends it
        to whatever SAP interface is configured. In this demo the PLM and the SAP gateway are <b>mocks</b>.
      </p>

      <DataFlow title="Data flow: ERP and external PLM" nodes={flow.nodes} edges={flow.edges} />

      <Stepper steps={STEPS} state={state} />

      <div className="grid">
        <CadEditor cad={cad} title="Engineering data entering the PLM (CAD JSON)" />

        <section className="card">
          <h2>CAD structure</h2>
          {cad.parsed.data ? <CadPreview cad={cad.parsed.data} /> : <p className="muted">No valid JSON yet.</p>}
          <p className="small muted">In a real landscape the PLM's own CAD integration creates this BOM. The import below stands in for it.</p>
          <div className="actions">
            <button className="primary" disabled={!cad.parsed.data || busy} onClick={doImport}>
              {busy === 'import' ? 'Importing…' : 'Load into external PLM →'}
            </button>
          </div>
        </section>

        <section className="card full">
          <h2>External PLM: engineering BOM <span className="muted">(MOCK)</span></h2>
          {!importRes && <p className="muted">Load the CAD data into the PLM to create the BOM.</p>}
          {importRes && !importRes.ok && (
            <ErrorBox res={importRes}
                      hint={importRes.data?.code === 'PLM_CONFLICT' ? 'Use "Bump revision" to export a new revision and run the flow again.' : null} />
          )}
          {importRes?.ok && bom && (
            <div className="two">
              <div>
                <p className="mapping">
                  <b>{importRes.data.cadInstances}</b> CAD instances
                  {importRes.data.excludedInstances > 0 && <> (<b>{importRes.data.excludedInstances}</b> excluded)</>}
                  {' → '}<b>{bom.items.length}</b> BOM items
                  {importRes.data.replacedExisting && <span className="pill info">replaced IN_WORK revision</span>}
                </p>
                <table>
                  <thead><tr><th>Item</th><th>Component</th><th className="num">Qty</th><th>Unit</th></tr></thead>
                  <tbody>
                    {bom.items.map((it, i) => (
                      <tr key={it.componentNumber}>
                        <td>{String((i + 1) * 10).padStart(4, '0')}</td><td>{it.componentNumber}</td>
                        <td className="num">{it.quantity}</td><td>{it.unit}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                {importRes.data.notes?.length > 0 && (
                  <><h3>Mapping notes</h3><ul className="notes">{importRes.data.notes.map((n) => <li key={n}>{n}</li>)}</ul></>
                )}
              </div>
              <div>
                <dl className="kv">
                  <dt>BOM</dt><dd><b>{bom.materialNumber}</b> rev {bom.revision}, plant {bom.plant}</dd>
                  <dt>Release state</dt>
                  <dd><span className={'pill ' + (bom.releaseState === 'RELEASED' ? 'ok' : 'warn')}>{bom.releaseState}</span></dd>
                  <dt>Change number</dt><dd>{bom.changeNumber || '–'}</dd>
                  <dt>Source</dt><dd>{bom.sourceSystem}</dd>
                </dl>
                <p className="hint">The PLM owns the release decision. The integration layer only sends <b>RELEASED</b> BOMs to SAP.</p>
                <div className="actions">
                  <button className="primary" disabled={busy || bom.releaseState !== 'IN_WORK'} onClick={doRelease}>
                    {busy === 'release' ? 'Releasing…' : 'Release in PLM'}
                  </button>
                  <button onClick={refreshBom} disabled={busy}>Reload from PLM</button>
                </div>
                {releaseRes && !releaseRes.ok && <ErrorBox res={releaseRes} />}
              </div>
            </div>
          )}
        </section>

        <section className="card full">
          <h2>Integration layer → SAP <span className="muted">(gateway: {gatewayMode})</span></h2>
          {!bom ? (
            <p className="muted">Load a BOM into the PLM first.</p>
          ) : (
            <>
              <div className="row gap">
                <button className="primary" disabled={busy} onClick={() => doTransfer(false)}>
                  {busy === 'transfer' ? 'Sending…' : 'Transfer ' + bom.materialNumber + ' rev ' + bom.revision + ' to SAP'}
                </button>
                <button disabled={busy || !transferRes} onClick={() => doTransfer(true)}
                        title="Re-send with the same Idempotency-Key to show duplicate protection">
                  Send again (same key)
                </button>
                <button disabled={busy || !transferRes?.data?.transferId} onClick={doStatus}>Check status</button>
                <span className="muted small">Idempotency-Key: <code>{idemKey}</code></span>
              </div>
              {bom.releaseState !== 'RELEASED' && !transferRes && (
                <p className="hint">Tip: try transferring <i>before</i> releasing. The integration layer rejects it with 422.</p>
              )}
              {transferRes && (transferRes.data?.transferId ? <TransferView res={transferRes} /> : <ErrorBox res={transferRes} />)}
              {statusRes && (
                <div className="status-box">
                  <h3>Status from GET /api/v1/bom-transfers/{'{id}'}</h3>
                  {statusRes.ok ? <TransferView res={statusRes} compact /> : <ErrorBox res={statusRes} />}
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </>
  );
}

function TransferView({ res, compact }) {
  const t = res.data;
  const cls = t.status === 'SUCCEEDED' ? 'ok' : t.status === 'FAILED_TRANSIENT' ? 'warn' : 'err';
  return (
    <div className="transfer">
      <div className="row gap">
        <span className={'pill ' + cls}>HTTP {res.status}</span>
        <span className={'pill ' + cls}>{t.status}</span>
        {t.duplicate && <span className="pill info">duplicate: SAP not called again</span>}
        <span className="pill info">{t.gatewayMode}</span>
      </div>
      <dl className="kv">
        <dt>Target reference</dt><dd>{t.targetReference || '–'}</dd>
        <dt>Attempts</dt><dd>{t.attempts}</dd>
        <dt>Transfer ID</dt><dd><code>{t.transferId}</code></dd>
        {!compact && <><dt>Correlation ID</dt><dd><code>{t.correlationId}</code></dd></>}
      </dl>
      {t.messages?.length > 0 && <ul className="notes">{t.messages.map((m, i) => <li key={i}>{m}</li>)}</ul>}
    </div>
  );
}

/** Builds the data-flow view (CAD data -> external PLM -> integration layer -> SAP ERP). */
function dataFlow({ cadData, importRes, bom, transferRes, statusRes, idemKey, gatewayMode }) {
  const a = cadData?.assembly;
  const comps = Array.isArray(a?.components) ? a.components : [];
  const t = transferRes?.data?.transferId ? transferRes.data : null;
  const transferErr = transferRes && !t ? transferRes.data : null;
  const released = bom?.releaseState === 'RELEASED';
  const sent = t?.status === 'SUCCEEDED';

  const nodes = [
    {
      id: 'cad', system: 'CAD data', role: 'via the PLM\'s own CAD integration',
      state: a ? 'done' : 'fail',
      lines: a ? [`${a.partNumber} rev ${a.revision}`, `${comps.length} instances`] : [],
    },
    {
      id: 'plm', system: 'External PLM', role: 'owns data + release decision', mock: true,
      state: importRes && !importRes.ok ? 'fail' : released ? 'done' : bom ? 'active' : '',
      lines: bom ? [
        `BOM ${bom.materialNumber} rev ${bom.revision} / ${bom.plant}`,
        `${bom.items.length} items`,
        `state ${bom.releaseState}`,
        bom.changeNumber ? `change ${bom.changeNumber}` : null,
      ].filter(Boolean) : [],
    },
    {
      id: 'int', system: 'Integration layer', role: 'validate, map, retry, track',
      state: transferErr ? 'fail' : t ? (sent ? 'done' : 'fail') : released ? 'active' : '',
      lines: t ? [
        `transfer ${t.transferId.slice(0, 8)}…`,
        `status ${t.status}${t.duplicate ? ' (duplicate)' : ''}`,
        `attempts ${t.attempts}`,
        `key ${t.idempotencyKey}`,
      ] : transferErr ? [`rejected: ${transferErr.code}`, ...(transferErr.details ?? []).slice(0, 2)] : bom ? [`key ${idemKey}`] : [],
    },
    {
      id: 'sap', system: 'SAP ERP / S/4HANA', role: 'material BOM for production', mock: gatewayMode === 'MOCK',
      state: sent ? 'done' : t ? 'fail' : '',
      lines: sent ? [
        `reference ${t.targetReference}`,
        statusRes?.ok ? `status checked: ${statusRes.data.status}` : 'status not checked yet',
      ] : t ? [t.messages?.[t.messages.length - 1] ?? t.status] : [],
    },
  ];

  const edges = [
    {
      label: 'CAD structure',
      state: importRes ? (importRes.ok ? 'done' : 'fail') : a ? 'active' : '',
      payload: importRes?.ok ? [`${importRes.data.cadInstances} instances → ${bom?.items.length ?? 0} items`] : [],
    },
    {
      label: released ? 'RELEASED BOM' : 'BOM (must be RELEASED)',
      state: t || transferErr ? (transferErr ? 'fail' : 'done') : released ? 'active' : '',
      payload: bom ? bom.items.map((i) => `${i.componentNumber} × ${i.quantity}`) : [],
    },
    {
      label: 'mapped request (demo contract)',
      state: sent ? 'done' : t ? 'fail' : '',
      payload: bom && t ? bom.items.map((i, n) => `${String((n + 1) * 10).padStart(4, '0')} ${i.componentNumber} ${i.quantity} PC`) : [],
    },
  ];
  return { nodes, edges };
}
