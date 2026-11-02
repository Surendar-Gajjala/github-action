import { useEffect, useId, useMemo, useRef, useState } from 'react';

export interface Option {
  value: number;
  label: string;
  description?: string;
}

interface Props {
  options: Option[];
  value: number[];
  onChange: (value: number[]) => void;
  placeholder?: string;
  loading?: boolean;
  disabled?: boolean;
  /** Called with the search text, e.g. to fetch options server-side. */
  onSearch?: (text: string) => void;
  ariaLabel?: string;
}

/**
 * Searchable multi-select dropdown with removable chips and keyboard support
 * (↑/↓ to move, Enter to toggle, Backspace removes the last chip, Esc closes).
 */
export function MultiSelect({ options, value, onChange, placeholder = 'Search…', loading, disabled, onSearch, ariaLabel }: Props) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const [active, setActive] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const listId = useId();

  // Remember labels of selected values even if the current option list (search result) no longer contains them.
  const [known, setKnown] = useState<Record<number, Option>>({});
  useEffect(() => {
    setKnown((k) => {
      const next = { ...k };
      options.forEach((o) => (next[o.value] = o));
      return next;
    });
  }, [options]);

  const filtered = useMemo(() => {
    const t = text.trim().toLowerCase();
    return t
      ? options.filter((o) => o.label.toLowerCase().includes(t) || o.description?.toLowerCase().includes(t))
      : options;
  }, [options, text]);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (!containerRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const toggle = (v: number) => onChange(value.includes(v) ? value.filter((x) => x !== v) : [...value, v]);

  function onKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((a) => Math.min(a + 1, filtered.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const opt = filtered[active];
      if (open && opt) toggle(opt.value);
    } else if (e.key === 'Escape') {
      setOpen(false);
    } else if (e.key === 'Backspace' && !text && value.length) {
      onChange(value.slice(0, -1));
    }
  }

  return (
    <div ref={containerRef} className="relative">
      <div
        className={`flex min-h-[38px] flex-wrap items-center gap-1 rounded-md border border-slate-300 bg-white px-2 py-1 shadow-sm focus-within:border-indigo-500 focus-within:ring-1 focus-within:ring-indigo-500 ${disabled ? 'bg-slate-100' : ''}`}
        onClick={() => !disabled && setOpen(true)}
      >
        {value.map((v) => (
          <span key={v} className="inline-flex items-center gap-1 rounded bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-800 ring-1 ring-inset ring-indigo-200">
            {known[v]?.label ?? `#${v}`}
            {!disabled && (
              <button
                type="button"
                aria-label={`Remove ${known[v]?.label ?? v}`}
                className="text-indigo-500 hover:text-indigo-900"
                onClick={(e) => {
                  e.stopPropagation();
                  toggle(v);
                }}
              >
                ×
              </button>
            )}
          </span>
        ))}
        <input
          role="combobox"
          aria-label={ariaLabel ?? placeholder}
          aria-expanded={open}
          aria-controls={listId}
          disabled={disabled}
          className="min-w-[120px] flex-1 border-0 bg-transparent px-1 py-1 text-sm outline-none focus:ring-0"
          placeholder={value.length ? '' : placeholder}
          value={text}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          onChange={(e) => {
            setText(e.target.value);
            setActive(0);
            setOpen(true);
            onSearch?.(e.target.value);
          }}
        />
      </div>
      {open && !disabled && (
        <ul id={listId} role="listbox" aria-multiselectable="true"
          className="absolute z-50 mt-1 max-h-64 w-full overflow-y-auto rounded-md border border-slate-200 bg-white py-1 shadow-lg">
          {loading && <li className="px-3 py-2 text-slate-500">Loading…</li>}
          {!loading && filtered.length === 0 && <li className="px-3 py-2 text-slate-500">No matches</li>}
          {filtered.map((o, i) => {
            const selected = value.includes(o.value);
            return (
              <li
                key={o.value}
                role="option"
                aria-selected={selected}
                className={`flex cursor-pointer items-center gap-2 px-3 py-1.5 ${i === active ? 'bg-indigo-50' : ''}`}
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => toggle(o.value)}
              >
                <input type="checkbox" readOnly checked={selected} tabIndex={-1} className="rounded border-slate-300 text-indigo-600" />
                <span className="font-medium text-slate-800">{o.label}</span>
                {o.description && <span className="truncate text-xs text-slate-500">{o.description}</span>}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
