// Thin wrapper around fetch for the demo backend.
// Every call is reported to `onLog` so the page can show the full API trace.
// These are the demo's own endpoints, NOT SAP endpoints.

export async function callApi(method, url, { body, headers = {}, onLog, flow } = {}) {
  const started = performance.now();
  const init = { method, headers: { ...headers } };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json';
    init.body = typeof body === 'string' ? body : JSON.stringify(body);
  }

  let status = 0;
  let data = null;
  let correlationId = null;
  let networkError = null;
  try {
    const res = await fetch(url, init);
    status = res.status;
    correlationId = res.headers.get('X-Correlation-ID');
    const text = await res.text();
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { raw: text };
    }
  } catch (e) {
    networkError = 'Backend not reachable. Is Spring Boot running on port 8080?';
  }

  const result = {
    method, url, status, data, correlationId, networkError, flow,
    ok: status >= 200 && status < 300,
    ms: Math.round(performance.now() - started),
    requestBody: init.body ? safeParse(init.body) : undefined,
    time: new Date().toLocaleTimeString(),
  };
  onLog?.(result);
  return result;
}

function safeParse(s) {
  try { return JSON.parse(s); } catch { return s; }
}

const enc = encodeURIComponent;
const bomPath = ({ materialNumber, revision, plant }) =>
  `/api/v1/plm/boms/${enc(materialNumber)}/${enc(revision)}`;

export const api = {
  info: () => callApi('GET', '/api/v1/info'),

  // ---- CAD path: CAD -> SAP ECTR -> SAP backend (simulated) ----
  ectr: {
    createDocuments: (cadJson, onLog) =>
      callApi('POST', '/api/v1/ectr/sessions', { body: cadJson, onLog, flow: 'CAD' }),
    checkIn: (id, onLog) => callApi('POST', `/api/v1/ectr/sessions/${enc(id)}/checkin`, { onLog, flow: 'CAD' }),
    createMaterials: (id, onLog) => callApi('POST', `/api/v1/ectr/sessions/${enc(id)}/materials`, { onLog, flow: 'CAD' }),
    maintainBom: (id, onLog) => callApi('POST', `/api/v1/ectr/sessions/${enc(id)}/bom`, { onLog, flow: 'CAD' }),
    get: (id, onLog) => callApi('GET', `/api/v1/ectr/sessions/${enc(id)}`, { onLog, flow: 'CAD' }),
  },

  // ---- ERP path: External PLM -> integration layer -> SAP ERP ----
  erp: {
    importToPlm: (cadJson, onLog) => callApi('POST', '/api/v1/plm/imports', { body: cadJson, onLog, flow: 'ERP' }),
    getBom: (key, onLog) => callApi('GET', `${bomPath(key)}?plant=${enc(key.plant)}`, { onLog, flow: 'ERP' }),
    release: (key, onLog) => callApi('POST', `${bomPath(key)}/release?plant=${enc(key.plant)}`, { onLog, flow: 'ERP' }),
    transfer: ({ materialNumber, revision, plant }, idempotencyKey, onLog) =>
      callApi('POST', '/api/v1/bom-transfers', {
        body: { materialNumber, revision, plant },
        headers: { 'Idempotency-Key': idempotencyKey },
        onLog,
        flow: 'ERP',
      }),
    transferStatus: (transferId, onLog) =>
      callApi('GET', `/api/v1/bom-transfers/${enc(transferId)}`, { onLog, flow: 'ERP' }),
  },
};
