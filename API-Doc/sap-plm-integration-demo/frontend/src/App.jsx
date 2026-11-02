import { useCallback, useEffect, useState } from 'react';
import { api } from './api.js';
import { ApiLog } from './components/shared.jsx';
import CadToPlmFlow from './cad-to-plm/CadToPlmFlow.jsx';
import ErpToPlmFlow from './erp-to-plm/ErpToPlmFlow.jsx';

// Tab ids are also the URL hash: #cad-to-plm, #erp-to-plm
const TABS = [
  { id: 'cad-to-plm', label: 'CAD to PLM', sub: 'CAD → SAP ECTR → SAP backend · backend package cad' },
  { id: 'erp-to-plm', label: 'ERP to PLM', sub: 'External PLM → Integration layer → SAP ERP/S/4HANA · backend package erp' },
];
const tabFromHash = () => TABS.find((t) => '#' + t.id === window.location.hash)?.id ?? TABS[0].id;

export default function App() {
  const [tab, setTab] = useState(tabFromHash);
  const [info, setInfo] = useState(null);
  const [log, setLog] = useState([]);

  const onLog = useCallback((entry) => setLog((l) => [entry, ...l].slice(0, 60)), []);

  useEffect(() => {
    api.info().then((r) => setInfo(r.ok ? r.data : { gatewayMode: 'backend offline', ectrBackend: 'backend offline' }));
    const onHash = () => setTab(tabFromHash());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  function select(id) {
    setTab(id);
    window.history.replaceState(null, '', '#' + id);
  }

  return (
    <>
      <header className="top">
        <div className="wrap row">
          <h1>SAP PLM integration: data flows</h1>
          <span className="pill warn">ECTR + SAP backend: {info?.ectrBackend ?? '…'}</span>
          <span className="pill warn">SAP gateway: {info?.gatewayMode ?? '…'}</span>
        </div>
      </header>

      <main className="wrap">
        <div className="banner">
          <b>Learning demo.</b> The CAD JSON format, ECTR steps, PLM store and SAP systems are <b>mocks</b>. Calls go to this
          demo's own API, not to SAP. Keys like <code>CAD/47…</code>, <code>MOCK-CS-…</code> and <code>MOCK-BOM-…</code> are not SAP records.
        </div>

        <div className="tabs" role="tablist">
          {TABS.map((t) => (
            <button key={t.id} role="tab" aria-selected={tab === t.id} className={'tab' + (tab === t.id ? ' active' : '')}
                    onClick={() => select(t.id)}>
              <b>{t.label}</b>
              <span>{t.sub}</span>
            </button>
          ))}
        </div>

        {/* Both flows stay mounted so switching tabs keeps their state. */}
        <div hidden={tab !== 'cad-to-plm'}><CadToPlmFlow onLog={onLog} /></div>
        <div hidden={tab !== 'erp-to-plm'}><ErpToPlmFlow onLog={onLog} gatewayMode={info?.gatewayMode ?? '…'} /></div>

        <section className="card full" style={{ marginTop: 16 }}>
          <div className="row between">
            <h2>API calls ({log.length})</h2>
            <button onClick={() => setLog([])} disabled={!log.length}>Clear</button>
          </div>
          <ApiLog log={log} />
        </section>
      </main>
    </>
  );
}
