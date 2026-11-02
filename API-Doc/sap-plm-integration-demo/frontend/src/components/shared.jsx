import { useCallback, useEffect, useMemo, useState } from 'react';

export const SAMPLES = [
  { label: 'MOTOR-100 rev D (valid)', url: '/samples/motor-100-revD.cad.json' },
  { label: 'Broken export (errors)', url: '/samples/motor-100-invalid.cad.json' },
];

/** CAD JSON text + parsed result + sample loading. `onChange` lets a flow reset its later steps. */
export function useCadJson(onChange) {
  const [text, setText] = useState('');

  const parsed = useMemo(() => {
    if (!text.trim()) return { error: 'Paste or load CAD JSON' };
    try {
      const data = JSON.parse(text);
      if (!data?.assembly) return { error: 'JSON has no "assembly" object' };
      return { data };
    } catch (e) {
      return { error: 'Invalid JSON: ' + e.message };
    }
  }, [text]);

  const update = useCallback((t) => { setText(t); onChange?.(); }, [onChange]);

  const loadSample = useCallback(async (url) => {
    const res = await fetch(url);
    update(await res.text());
  }, [update]);

  useEffect(() => { loadSample(SAMPLES[0].url); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const bumpRevision = useCallback(() => {
    if (!parsed.data) return;
    const d = structuredClone(parsed.data);
    const rev = String(d.assembly.revision || 'A');
    const last = rev.charCodeAt(rev.length - 1);
    d.assembly.revision = last >= 90 ? rev + 'A' : rev.slice(0, -1) + String.fromCharCode(last + 1);
    update(JSON.stringify(d, null, 2));
  }, [parsed.data, update]);

  return { text, setText: update, parsed, loadSample, bumpRevision };
}

export function CadEditor({ cad, title = '1. CAD assembly JSON' }) {
  async function onFile(e) {
    const file = e.target.files?.[0];
    if (file) cad.setText(await file.text());
    e.target.value = '';
  }
  return (
    <section className="card">
      <h2>{title}</h2>
      <div className="row gap">
        {SAMPLES.map((s) => <button key={s.url} onClick={() => cad.loadSample(s.url)}>{s.label}</button>)}
        <label className="filebtn">Upload .json<input type="file" accept=".json,application/json" onChange={onFile} /></label>
        <button onClick={cad.bumpRevision} disabled={!cad.parsed.data} title="Increase assembly revision, e.g. D → E">
          Bump revision
        </button>
      </div>
      <textarea spellCheck="false" value={cad.text} onChange={(e) => cad.setText(e.target.value)} aria-label="CAD JSON" />
      {cad.parsed.error && <p className="err-text">{cad.parsed.error}</p>}
    </section>
  );
}

export function CadPreview({ cad }) {
  const a = cad.assembly || {};
  const comps = Array.isArray(a.components) ? a.components : [];
  return (
    <>
      <dl className="kv">
        <dt>Assembly</dt><dd><b>{a.partNumber}</b> rev {a.revision}, plant {a.plant}</dd>
        <dt>Description</dt><dd>{a.description || '–'}</dd>
        <dt>Source</dt><dd>{cad.source?.cadSystem} · {cad.source?.fileName} · {cad.source?.exportedBy}</dd>
      </dl>
      <div className="scroll">
        <table>
          <thead><tr><th>Instance</th><th>Part</th><th>Description</th><th className="num">Qty</th><th>Unit</th><th>In BOM?</th></tr></thead>
          <tbody>
            {comps.map((c, i) => (
              <tr key={(c.instanceId || '') + i} className={c.excludeFromBom ? 'dim' : ''}>
                <td>{c.instanceId}</td>
                <td>{c.partNumber}</td>
                <td>{c.description}</td>
                <td className={'num' + (c.quantity !== undefined && !(Number(c.quantity) > 0) ? ' bad' : '')}>{c.quantity ?? '1'}</td>
                <td>{c.unit || 'EA'}</td>
                <td>{c.excludeFromBom ? 'no' : 'yes'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function Stepper({ steps, state }) {
  const current = steps.find((s) => state[s.id] !== 'done')?.id;
  return (
    <nav className="stepper" style={{ '--cols': steps.length }} aria-label="Flow steps">
      {steps.map((s, i) => (
        <div key={s.id} className={'step ' + (state[s.id] || '') + (current === s.id ? ' current' : '')}>
          <b>{s.title}</b>
          <span>{s.sub}</span>
          {i < steps.length - 1 && <i className="arrow" aria-hidden>→</i>}
        </div>
      ))}
    </nav>
  );
}

export function ErrorBox({ res, hint }) {
  if (res.networkError) return <div className="errbox">{res.networkError}</div>;
  const d = res.data || {};
  return (
    <div className="errbox">
      <div className="row gap">
        <span className="pill err">HTTP {res.status}</span>
        <b>{d.code || 'Error'}</b>
      </div>
      {d.message && <p>{d.message}</p>}
      {d.details?.length > 0 && <ul>{d.details.map((x, i) => <li key={i}>{x}</li>)}</ul>}
      {hint && <p className="hint">{hint}</p>}
    </div>
  );
}

export function ApiLog({ log }) {
  if (!log.length) return <p className="muted">No calls yet.</p>;
  return (
    <div className="log">
      {log.map((e, i) => (
        <details key={log.length - i}>
          <summary>
            <span className="muted">{e.time}</span>
            {e.flow && <span className={'pill ' + (e.flow === 'CAD' ? 'info' : 'neutral')}>{e.flow}</span>}
            <b className="method">{e.method}</b>
            <code className="url">{e.url}</code>
            <span className={'pill ' + (e.ok ? 'ok' : e.status >= 500 || !e.status ? 'err' : 'warn')}>{e.status || 'ERR'}</span>
            <span className="muted">{e.ms} ms</span>
          </summary>
          {e.requestBody !== undefined && (<><h4>Request</h4><pre>{JSON.stringify(e.requestBody, null, 2)}</pre></>)}
          <h4>Response{e.correlationId ? ` (X-Correlation-ID: ${e.correlationId})` : ''}</h4>
          <pre>{e.networkError || JSON.stringify(e.data, null, 2)}</pre>
        </details>
      ))}
    </div>
  );
}
