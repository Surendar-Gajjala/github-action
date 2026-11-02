import { Search } from 'lucide-react';

interface Props {
  /** Accessible name, e.g. "Search items". */
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
}

export function SearchBox({ label, value, onChange, placeholder }: Props) {
  return (
    <label className="relative block w-full max-w-[360px]">
      <span className="sr-only">{label}</span>
      <Search
        className="pointer-events-none absolute top-1/2 left-3.5 size-[18px] -translate-y-1/2 text-gray-500"
        strokeWidth={1.75}
        aria-hidden
      />
      <input
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        className="w-full rounded-lg border border-gray-200 bg-white py-2.5 pr-3 pl-11 text-[16px] text-gray-900 placeholder:text-gray-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 focus:outline-none"
      />
    </label>
  );
}
