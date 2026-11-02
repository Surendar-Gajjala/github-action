import type { ChangeDiff, PartSnapshot } from '../../api/types';

function PartList({ parts, tone }: { parts: PartSnapshot[]; tone: 'add' | 'remove' | 'same' }) {
  const style = {
    add: 'bg-emerald-50 text-emerald-800 ring-emerald-200',
    remove: 'bg-rose-50 text-rose-800 ring-rose-200 line-through',
    same: 'bg-slate-50 text-slate-600 ring-slate-200',
  }[tone];
  const sign = { add: '+', remove: '−', same: '' }[tone];
  return (
    <ul className="flex flex-wrap gap-1.5">
      {parts.map((p) => (
        <li key={p.partNumber} className={`rounded px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${style}`}>
          {sign} {p.partNumber} · {p.name}
        </li>
      ))}
    </ul>
  );
}

/** Item header and parts changes between the last approved revision and the working revision. */
export function ChangeDiffView({ diff, section = 'all' }: { diff: ChangeDiff; section?: 'all' | 'item' | 'parts' }) {
  const showItem = section !== 'parts';
  const showParts = section !== 'item';
  return (
    <div className="space-y-4">
      <p className="text-slate-600">
        {diff.baseRevision ? (
          <>Changes from approved revision <b>{diff.baseRevision}</b> to working revision <b>{diff.targetRevision}</b></>
        ) : (
          <>New item — first approval of revision <b>{diff.targetRevision}</b></>
        )}
        {diff.changeRequest && <> · {diff.changeRequest.number} ({diff.changeRequest.status.toLowerCase()})</>}
      </p>
      {showItem && (
        <div>
          <p className="label">Item changes</p>
          {diff.fieldChanges.length === 0 ? (
            <p className="text-slate-500">No header changes.</p>
          ) : (
            <table className="min-w-full text-sm">
              <thead>
                <tr><th className="th">Field</th><th className="th">Approved</th><th className="th">Proposed</th></tr>
              </thead>
              <tbody>
                {diff.fieldChanges.map((c) => (
                  <tr key={c.field} className="border-t border-slate-100">
                    <td className="td font-medium capitalize">{c.field}</td>
                    <td className="td text-rose-700">{c.oldValue ?? <i className="text-slate-400">empty</i>}</td>
                    <td className="td text-emerald-700">{c.newValue ?? <i className="text-slate-400">empty</i>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
      {showParts && (
        <div className="space-y-2">
          <p className="label">Parts changes</p>
          {diff.partsAdded.length === 0 && diff.partsRemoved.length === 0 && <p className="text-slate-500">No parts changes.</p>}
          {diff.partsAdded.length > 0 && <div><p className="mb-1 text-xs text-slate-500">Added</p><PartList parts={diff.partsAdded} tone="add" /></div>}
          {diff.partsRemoved.length > 0 && <div><p className="mb-1 text-xs text-slate-500">Removed</p><PartList parts={diff.partsRemoved} tone="remove" /></div>}
          {diff.unchangedParts.length > 0 && <div><p className="mb-1 text-xs text-slate-500">Unchanged</p><PartList parts={diff.unchangedParts} tone="same" /></div>}
        </div>
      )}
    </div>
  );
}
