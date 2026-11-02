import type { ReactNode } from 'react';

export type BadgeTone = 'gray' | 'green' | 'amber' | 'red' | 'blue' | 'violet';

const TONES: Record<BadgeTone, string> = {
  gray: 'border-gray-200 bg-gray-50 text-gray-700',
  green: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  amber: 'border-amber-200 bg-amber-50 text-amber-700',
  red: 'border-red-200 bg-red-50 text-red-700',
  blue: 'border-blue-200 bg-blue-50 text-blue-700',
  violet: 'border-violet-200 bg-violet-50 text-violet-700',
};

interface Props {
  tone?: BadgeTone;
  children: ReactNode;
}

export function Badge({ tone = 'gray', children }: Props) {
  return (
    <span
      className={`inline-flex items-center rounded border px-2 py-0.5 text-[13px] font-semibold whitespace-nowrap ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}
