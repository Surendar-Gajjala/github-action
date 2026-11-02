import { useRef, type KeyboardEvent } from 'react';

export interface TabDefinition<K extends string> {
  key: K;
  label: string;
}

/** Accessible tab list (WAI-ARIA tabs pattern) with arrow-key navigation. */
export function Tabs<K extends string>({
  tabs,
  active,
  onChange,
}: {
  tabs: TabDefinition<K>[];
  active: K;
  onChange: (key: K) => void;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  function onKeyDown(event: KeyboardEvent, index: number) {
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!delta) return;
    event.preventDefault();
    const next = (index + delta + tabs.length) % tabs.length;
    onChange(tabs[next].key);
    refs.current[next]?.focus();
  }

  return (
    <div className="border-b border-slate-200">
      <nav role="tablist" aria-label="Onboarding sections" className="-mb-px flex gap-1 overflow-x-auto">
        {tabs.map((tab, index) => {
          const selected = tab.key === active;
          return (
            <button
              key={tab.key}
              ref={(el) => (refs.current[index] = el)}
              id={`tab-${tab.key}`}
              role="tab"
              type="button"
              aria-selected={selected}
              aria-controls={`panel-${tab.key}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(tab.key)}
              onKeyDown={(e) => onKeyDown(e, index)}
              className={`whitespace-nowrap border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
                selected
                  ? 'border-indigo-600 text-indigo-700'
                  : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </nav>
    </div>
  );
}
