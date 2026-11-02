import { AlertCircle } from 'lucide-react';

import { getErrorMessage } from '../../services/enterpriseQuery';

interface Props {
  title: string;
  error: unknown;
}

export function LoadError({ title, error }: Props) {
  return (
    <div role="alert" className="mx-10 flex items-start gap-3 rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
      <AlertCircle className="mt-0.5 size-5 shrink-0" aria-hidden />
      <div>
        <p className="font-semibold">{title} could not be loaded</p>
        <p className="mt-0.5 text-sm">{getErrorMessage(error)}</p>
      </div>
    </div>
  );
}
