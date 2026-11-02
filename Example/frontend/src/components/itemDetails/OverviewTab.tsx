import type { OverviewField, OverviewSection } from '../../types/itemDetails';
import { dash } from '../table/cells';
import { Badge, type BadgeTone } from '../ui/Badge';

interface Props {
  sections: OverviewSection[];
}

function riskTone(value: string): BadgeTone {
  switch (value.toUpperCase()) {
    case 'LOW':
      return 'green';
    case 'MEDIUM':
      return 'amber';
    case 'HIGH':
      return 'red';
    default:
      return 'gray';
  }
}

function FieldValue({ field, isRisk }: { field: OverviewField; isRisk: boolean }) {
  if (field.value === null) return dash;
  return isRisk ? <Badge tone={riskTone(field.value)}>{field.value}</Badge> : <>{field.value}</>;
}

/** All item properties, one card per section, as label / value pairs. */
export function OverviewTab({ sections }: Props) {
  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
      {sections.map((section) => {
        const isRisk = section.title === 'Risk';
        const isNotes = section.title === 'Notes';
        // aria-labelledby takes space-separated ids, so multi-word titles need a slug.
        const headingId = `section-${section.title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
        return (
          <section
            key={section.title}
            aria-labelledby={headingId}
            className={`rounded-xl border border-gray-200 bg-white ${isNotes ? 'xl:col-span-2' : ''}`}
          >
            <h2 id={headingId} className="border-b border-gray-200 px-6 py-3.5 text-[17px] font-semibold text-gray-900">
              {section.title}
            </h2>
            <dl className="divide-y divide-gray-100">
              {section.fields.map((field) => (
                <div key={field.label} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-4 px-6 py-3 text-[15px]">
                  <dt className="text-gray-500">{field.label}</dt>
                  <dd className="min-w-0 break-words text-gray-900">
                    <FieldValue field={field} isRisk={isRisk} />
                    {field.reason && <p className="mt-1 text-sm text-gray-500">{field.reason}</p>}
                  </dd>
                </div>
              ))}
            </dl>
          </section>
        );
      })}
    </div>
  );
}
