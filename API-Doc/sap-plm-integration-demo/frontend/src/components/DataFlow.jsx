import { Fragment } from 'react';

/**
 * Data-flow view: systems as boxes, arrows labelled with the data that moves between them.
 *
 * nodes: [{ id, system, role, lines: [string], state: '' | 'active' | 'done' | 'fail', mock }]
 * edges: [{ label, payload: [string], state }]   (edges[i] connects nodes[i] -> nodes[i+1])
 */
export default function DataFlow({ title, nodes, edges }) {
  return (
    <section className="card dataflow" aria-label={title}>
      <h2>{title}</h2>
      <div className="df-row" style={{ '--df-cols': Array(nodes.length).fill('minmax(0, 1fr)').join(' minmax(110px, .8fr) ') }}>
        {nodes.map((n, i) => (
          <Fragment key={n.id}>
            <div className={'df-node ' + (n.state || '')}>
              <div className="df-system">
                {n.system}
                {n.mock && <span className="pill warn df-mock">MOCK</span>}
              </div>
              <div className="df-role">{n.role}</div>
              <ul className="df-lines">
                {n.lines.length ? n.lines.map((l, j) => <li key={j}>{l}</li>) : <li className="muted">no data yet</li>}
              </ul>
            </div>
            {i < nodes.length - 1 && <Edge edge={edges[i]} />}
          </Fragment>
        ))}
      </div>
    </section>
  );
}

function Edge({ edge }) {
  return (
    <div className={'df-edge ' + (edge?.state || '')}>
      <div className="df-label">{edge?.label}</div>
      <div className="df-arrow" aria-hidden />
      {edge?.payload?.length > 0 && (
        <ul className="df-payload">{edge.payload.map((p, i) => <li key={i}><code>{p}</code></li>)}</ul>
      )}
    </div>
  );
}
