import type { BadgeTone } from './Badge';

/** Badge colour for a free-text item status label (shared by Items and Item Hierarchy). */
export function statusTone(status: string): BadgeTone {
  const s = status.toLowerCase();
  if (s.includes('production')) return 'green';
  if (s.includes('design')) return 'amber';
  if (s.includes('conditional')) return 'blue';
  if (s.includes('obsolete') || s.includes('inactive') || s === 'eol') return 'red';
  return 'gray';
}
