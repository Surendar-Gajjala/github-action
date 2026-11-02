import { useState, type ReactNode } from 'react';

import { useRapidRatingsDetails, useRapidRatingsSearch } from '../../hooks/useCompanyDetails';
import type { RapidRatingsCompany, RapidRatingsDetails } from '../../types/rapidRatings';
import { LoadError } from '../table/LoadError';
import { dash } from '../table/cells';
import { PERIOD_COLUMNS, RATING_COLUMNS, type HistoryColumn, externalLink, formatNumber } from './rapidRatingsColumns';

interface Props {
  /** The Z2 company name used to find the RapidRatings company; null when the company has none. */
  companyName: string | null;
}

/**
 * Demo: when set, every company shows this RapidRatings company instead of searching by name
 * (only one Z2 company name exists in the RapidRatings sandbox). `KawjbGJ` is Rite Aid
 * Corporation in the sandbox. Set to null to look companies up by name again.
 */
export const FIXED_RAPIDRATINGS_COMPANY: { rrid: string; name: string } | null = { rrid: 'KawjbGJ', name: 'Rite Aid Corporation' };

/**
 * RapidRatings data for one Z2 company, read live from RapidRatings when this tab is opened.
 * Nothing is stored or written to Z2.
 */
export function RapidRatingsTab({ companyName }: Props) {
  if (FIXED_RAPIDRATINGS_COMPANY !== null) {
    const { rrid, name } = FIXED_RAPIDRATINGS_COMPANY;
    return (
      <div className="flex flex-col gap-5">
        <Notice>
          Demo data: every company shows the RapidRatings sandbox company <strong className="text-gray-900">{name}</strong> (RRID {rrid}).
        </Notice>
        <RapidRatingsCompanyData rrid={rrid} />
      </div>
    );
  }
  return <RapidRatingsByName companyName={companyName} />;
}

/**
 * Finds the RapidRatings company by name: an exact name match is shown directly, otherwise the
 * user chooses among the companies whose name contains it.
 */
function RapidRatingsByName({ companyName }: Props) {
  const search = useRapidRatingsSearch(companyName);
  const [chosenRrid, setChosenRrid] = useState<string | null>(null);

  if (companyName === null) return <Notice>This company has no name to look up in RapidRatings.</Notice>;
  if (search.isError) return <LoadError title="RapidRatings company search" error={search.error} />;
  if (search.isLoading || !search.data) return <p className="text-gray-500">Searching RapidRatings for “{companyName}”…</p>;

  const { searchTerm, candidates, exactMatch } = search.data;
  if (searchTerm === null) return <Notice>The company name is too short to search RapidRatings (3 characters minimum).</Notice>;
  if (candidates.length === 0) return <Notice>No RapidRatings company matches “{searchTerm}”.</Notice>;

  const rrid = chosenRrid ?? exactMatch?.rrid ?? null;

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-gray-200 bg-white px-6 py-4 text-[15px]">
        <label htmlFor="rapidratings-company" className="font-semibold text-gray-900">
          RapidRatings company
        </label>
        <select
          id="rapidratings-company"
          value={rrid ?? ''}
          onChange={(event) => setChosenRrid(event.target.value || null)}
          className="min-w-0 max-w-full rounded-md border border-gray-300 bg-white px-3 py-1.5 text-gray-900"
        >
          {rrid === null && <option value="">Choose a company…</option>}
          {candidates.map((candidate) => (
            <option key={candidate.rrid} value={candidate.rrid ?? ''}>
              {candidateLabel(candidate)}
            </option>
          ))}
        </select>
        <p className="text-gray-500">
          {exactMatch
            ? 'Matched by company name.'
            : `No exact name match: ${candidates.length} ${candidates.length === 1 ? 'company contains' : 'companies contain'} “${searchTerm}”. Choose one to view its data.`}
        </p>
      </div>

      {rrid !== null && <RapidRatingsCompanyData rrid={rrid} />}
    </div>
  );
}

function RapidRatingsCompanyData({ rrid }: { rrid: string }) {
  const details = useRapidRatingsDetails(rrid);
  if (details.isError) return <LoadError title="RapidRatings data" error={details.error} />;
  if (details.isLoading || !details.data) return <p className="text-gray-500">Loading RapidRatings data…</p>;
  return <RapidRatingsDetailsView details={details.data} />;
}

function candidateLabel(company: RapidRatingsCompany): string {
  const extras = [company.sourceCountry, company.ticker, company.rrid].filter(Boolean).join(' · ');
  return extras ? `${company.sourceCompanyName ?? 'Unnamed'} (${extras})` : (company.sourceCompanyName ?? 'Unnamed');
}

// ---- Details ----------------------------------------------------------------------------

interface Field {
  label: string;
  value: ReactNode | null;
}

const value = (v: string | number | null): string | null => (v === null ? null : typeof v === 'number' ? formatNumber(v) : v);

function RapidRatingsDetailsView({ details }: { details: RapidRatingsDetails }) {
  const { company: c, periods, ratings, retrievedAt } = details;
  const amountsIn = [c.currency, c.dataScale].filter(Boolean).join(' ');

  const cards: { title: string; fields: Field[] }[] = [
    {
      title: 'Profile',
      fields: [
        { label: 'RRID', value: c.rrid },
        { label: 'Source Company Name', value: c.sourceCompanyName },
        { label: 'Source Country', value: c.sourceCountry },
        { label: 'Ticker', value: c.ticker },
        { label: 'Exchange', value: c.exchange },
        { label: 'Sector', value: c.sector },
        { label: 'Source Address', value: c.address },
      ],
    },
    {
      title: 'Current Rating',
      fields: [
        { label: 'Current Rating Period', value: c.currentRatingId },
        { label: 'Current FHR', value: value(c.currentFhr) },
        { label: 'Current CHS', value: value(c.currentChs) },
        { label: 'Current PD (%)', value: value(c.currentProbabilityDefault) },
        { label: 'Current Financial Date', value: c.currentFinancialDate },
      ],
    },
    {
      title: 'Benchmark',
      fields: [
        { label: 'Sector Average FHR', value: value(c.sectorAverageFhr) },
        { label: 'Sector Average CHS', value: value(c.sectorAverageChs) },
      ],
    },
    {
      title: 'Data Context',
      fields: [
        { label: 'Currency', value: c.currency },
        { label: 'Data Scale', value: c.dataScale },
        { label: 'Data Confidentiality', value: c.dataConfidentiality },
        { label: 'Industry Classification System', value: c.industryClassificationSystem },
        { label: 'Industry Classification Code', value: c.industryClassificationCode },
        { label: 'External Source ID', value: c.externalSourceId },
        { label: 'External Source IDs', value: c.externalSourceIds.length ? c.externalSourceIds.join(', ') : null },
      ],
    },
    {
      title: 'Report Links',
      fields: [
        { label: 'FHR Report', value: externalLink(c.reportLinkFhr) },
        { label: 'Financial Dialogue Report', value: externalLink(c.reportLinkFinancialDialog) },
        { label: 'Peer Benchmark Report', value: externalLink(c.reportLinkPeerBenchmark) },
      ],
    },
    {
      title: 'URLs',
      fields: [
        { label: 'Company URL', value: externalLink(c.companyUrl) },
        { label: 'Financials URL', value: externalLink(c.financialsUrl) },
        { label: 'Periods URL', value: externalLink(c.periodsUrl) },
        { label: 'Ratings URL', value: externalLink(c.ratingsUrl) },
      ],
    },
  ];

  return (
    <div className="flex flex-col gap-5">
      <p className="text-sm text-gray-500">
        Read live from RapidRatings at {new Date(retrievedAt).toLocaleString()}. Not stored in Z2.
      </p>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        {cards.map((card) => (
          <FieldCard key={card.title} title={card.title} fields={card.fields} />
        ))}
      </div>

      <HistoryTable
        title="Latest Ratings"
        columns={RATING_COLUMNS}
        rows={c.latestRatings}
        rowKey={(r, i) => r.ratingId ?? `row-${i}`}
        emptyMessage="RapidRatings returned no latest ratings."
      />
      <HistoryTable
        title="Periods"
        note={amountsIn ? `Amounts in ${amountsIn}` : undefined}
        columns={PERIOD_COLUMNS}
        rows={periods}
        rowKey={(p, i) => p.periodId ?? `row-${i}`}
        emptyMessage="RapidRatings returned no periods."
      />
      <HistoryTable
        title="Ratings"
        columns={RATING_COLUMNS}
        rows={ratings}
        rowKey={(r, i) => r.ratingId ?? `row-${i}`}
        emptyMessage="RapidRatings returned no ratings."
      />
    </div>
  );
}

const slug = (title: string) => `rapidratings-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;

function FieldCard({ title, fields }: { title: string; fields: Field[] }) {
  const headingId = slug(title);
  return (
    <section aria-labelledby={headingId} className="rounded-xl border border-gray-200 bg-white">
      <h2 id={headingId} className="border-b border-gray-200 px-6 py-3.5 text-[17px] font-semibold text-gray-900">
        {title}
      </h2>
      <dl className="divide-y divide-gray-100">
        {fields.map((field) => (
          <div key={field.label} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-4 px-6 py-3 text-[15px]">
            <dt className="text-gray-500">{field.label}</dt>
            <dd className="min-w-0 break-words text-gray-900">{field.value ?? dash}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function HistoryTable<T>({
  title,
  note,
  columns,
  rows,
  rowKey,
  emptyMessage,
}: {
  title: string;
  note?: string;
  columns: HistoryColumn<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string;
  emptyMessage: string;
}) {
  const headingId = slug(title);
  return (
    <section aria-labelledby={headingId} className="rounded-xl border border-gray-200 bg-white">
      <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-gray-200 px-6 py-3.5">
        <h2 id={headingId} className="text-[17px] font-semibold text-gray-900">
          {title} <span className="ml-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700">{rows.length}</span>
        </h2>
        {note && <p className="text-sm text-gray-500">{note}</p>}
      </div>
      {rows.length === 0 ? (
        <p className="px-6 py-6 text-gray-500">{emptyMessage}</p>
      ) : (
        <div className="overflow-x-auto">
          <table aria-labelledby={headingId} className="w-full min-w-max border-collapse text-left text-[14px] text-gray-800">
            <thead className="bg-gray-50">
              <tr>
                {columns.map((column) => (
                  <th
                    key={column.id}
                    scope="col"
                    className={`border-r border-b border-gray-200 px-4 py-2.5 font-semibold whitespace-nowrap text-gray-900 first:pl-6 last:border-r-0 ${
                      column.align === 'right' ? 'text-right' : ''
                    }`}
                  >
                    {column.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={rowKey(row, index)} className="border-b border-gray-100 last:border-b-0 hover:bg-gray-50/70">
                  {columns.map((column) => (
                    <td
                      key={column.id}
                      className={`px-4 py-2.5 whitespace-nowrap first:pl-6 ${column.align === 'right' ? 'text-right tabular-nums' : ''}`}
                    >
                      {column.render(row) ?? dash}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function Notice({ children }: { children: ReactNode }) {
  return <p className="rounded-xl border border-gray-200 bg-white px-6 py-4 text-[15px] text-gray-600">{children}</p>;
}
