import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface Props {
  title: string;
  subtitle?: string;
}

export function PageHeader({ title, subtitle }: Props) {
  const navigate = useNavigate();

  return (
    <header className="border-b border-gray-200 px-10 pt-8 pb-6">
      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="Go back"
          className="-ml-1 rounded-md p-1 text-gray-700 hover:bg-gray-100"
        >
          <ArrowLeft className="size-[22px]" strokeWidth={1.75} aria-hidden />
        </button>
        <h1 className="text-[29px] font-semibold text-gray-900">{title}</h1>
      </div>
      {subtitle && <p className="mt-2 text-[17px] text-gray-500">{subtitle}</p>}
    </header>
  );
}
