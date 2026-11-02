/**
 * SYNTHETIC DEMO DATA. Not RapidRatings data.
 * Generates fictional records that follow the verified response *structures* so the UI can be
 * developed without credentials. Values are seeded random numbers; company names are fictional.
 * Behaviour mirrors verified provider rules (page sizes, trailing-slash paths, name >= 3 chars,
 * unknown query parameters -> 400, past-the-end pages -> 404/500).
 */
import type { HostKey } from "../apiActivity";
import type { ApiError, OutreachCursorPage, RridataPage } from "@/types/api";
import type { Company } from "@/types/company";
import type { Portfolio } from "@/types/portfolio";
import type { Period } from "@/types/period";
import type { Rating } from "@/types/rating";
import type { OutreachRequest, OutreachRequestCreate } from "@/types/outreach";

const BASE = "https://demo.invalid/v2.0";
const LEVELS = ["Strong", "Average", "Weak"];
const PERIODS = ["Q1", "Q2", "Q3", "YE"];

function rng(seed: number) {
  let s = seed % 2147483647 || 1;
  return () => ((s = (s * 16807) % 2147483647) - 1) / 2147483646;
}

const NAMES = [
  "Demo Alloy Works Ltd", "Demo Circuit Components Inc", "Demo Logistics Group", "Demo Polymer Holdings",
  "Demo Precision Castings", "Demo Semiconductor Supply", "Demo Packaging Solutions", "Demo Energy Systems",
  "Demo Fastener Corp", "Demo Optics Manufacturing", "Demo Chemical Partners", "Demo Textile Mills",
  "Demo Rail Services", "Demo Battery Technologies", "Demo Marine Equipment", "Demo Aerospace Parts",
  "Demo Food Ingredients", "Demo Medical Devices", "Demo Paper Products", "Demo Robotics Co",
  "Demo Steel Distribution", "Demo Glass Industries", "Demo Cable & Wire",
];
const SECTORS = ["Metals and Fabrication", "Technology Hardware", "Transportation", "Chemicals", "Industrial Machinery"];

const periodIds = (() => {
  const ids: { eqyYear: number; period: string }[] = [];
  for (let y = 2024; y >= 2021; y--) for (const p of [...PERIODS].reverse()) ids.push({ eqyYear: y, period: p });
  return ids; // newest first, 16 periods
})();

const companies: Company[] = NAMES.map((name, i) => {
  const id = `DEMO${String(i + 1).padStart(3, "0")}`;
  const ratings = buildRatings(id, i + 1);
  return {
    id,
    name,
    country: ["USA", "DEU", "GBR", "JPN", "MEX"][i % 5],
    ticker: "",
    exchange: "",
    sector: SECTORS[i % SECTORS.length],
    href: `${BASE}/companies/${id}/`,
    financials: `${BASE}/companies/${id}/financials/`,
    periods: `${BASE}/companies/${id}/periods/`,
    ratings: `${BASE}/companies/${id}/ratings/`,
    latestRatings: ratings.slice(0, 5),
    reportLinks: {},
    address: "",
    industryClassificationSystem: null,
    industryClassificationCode: null,
    currency: "USD",
    dataScale: "millions",
    dataConfidential: "DEMO",
    externalSourceId: null,
    externalSourceIds: [],
  };
});

function buildRatings(id: string, seed: number): Rating[] {
  const r = rng(seed * 7919);
  let fhr = Math.round(30 + r() * 50);
  return periodIds.map(({ eqyYear, period }, idx) => {
    const prev = fhr;
    fhr = Math.max(1, Math.min(99, fhr + Math.round((r() - 0.5) * 12)));
    const pd = Number((Math.max(0.01, (100 - fhr) / 20) * r()).toFixed(4));
    const termPd: Record<string, number> = {};
    for (let y = 2; y <= 10; y++) termPd[`year${y}`] = Number((pd * y * 1.6).toFixed(2));
    return {
      id: `${eqyYear}${period}`,
      href: `${BASE}/companies/${id}/ratings/${eqyYear}${period}/`,
      eqyYear,
      period,
      financialDate: `${eqyYear}-${["03-31", "06-30", "09-30", "12-31"][PERIODS.indexOf(period)]}`,
      reportingPeriod: `${eqyYear + (period === "YE" ? 1 : 0)}-${period === "YE" ? "02-15" : "11-15"}`,
      fhr,
      chs: Math.max(1, Math.min(99, fhr + Math.round((r() - 0.5) * 10))),
      probabilityDefault: pd,
      termPd,
      delta: idx === periodIds.length - 1 ? null : fhr - prev,
      simulatedFhr: fhr + Math.round((r() - 0.5) * 4),
      simulatedFhrDelta: Math.round((r() - 0.5) * 6),
      operatingProfitability: Math.round(r() * 100),
      netProfitability: Math.round(r() * 100),
      capitalStructureEfficiency: Math.round(r() * 100),
      costStructureEfficiency: Math.round(r() * 100),
      leverage: LEVELS[Math.floor(r() * 3)],
      liquidity: LEVELS[Math.floor(r() * 3)],
      earningsPerformance: LEVELS[Math.floor(r() * 3)],
    };
  });
}

function buildPeriods(id: string, seed: number): Period[] {
  const r = rng(seed * 104729);
  let revenue = 500 + r() * 5000;
  return periodIds.map(({ eqyYear, period }) => {
    revenue *= 0.97 + r() * 0.06;
    const assets = revenue * (0.6 + r() * 0.6);
    const liabilities = assets * (0.3 + r() * 0.5);
    const round = (n: number) => Math.round(n * 10) / 10;
    return {
      id: `${eqyYear}${period}`,
      href: `${BASE}/companies/${id}/periods/${eqyYear}${period}/`,
      eqyYear,
      period,
      financialDate: `${eqyYear}-${["03-31", "06-30", "09-30", "12-31"][PERIODS.indexOf(period)]}`,
      currency: "USD",
      salesRevenue: round(revenue),
      earningsBeforeInterestAndTax: round(revenue * (0.02 + r() * 0.1)),
      interestExpense: round(liabilities * 0.02),
      interestReceived: round(r() * 10),
      companyTaxExpense: round(revenue * 0.01),
      abnormalItems: r() > 0.7 ? round((r() - 0.5) * 30) : null,
      netProfitAfterTax: round(revenue * (r() * 0.08 - 0.01)),
      bankCashBalances: round(assets * 0.05),
      totalCurrentAssets: round(assets * 0.35),
      fixedAssets: round(assets * 0.4),
      financialAssets: null,
      totalAssets: round(assets),
      debitOwnedWithinOneYear: round(liabilities * 0.1),
      totalCurrentLiabilities: round(liabilities * 0.4),
      termLoans: round(liabilities * 0.45),
      totalLiabilities: round(liabilities),
      totalShareholderEquity: round(assets - liabilities),
      cashFromOperationsFromCashFlow: null,
      netInvestingCashFlow: round(-revenue * 0.04),
      netFundingCashFlow: round((r() - 0.5) * revenue * 0.05),
    };
  });
}

const portfolios: Portfolio[] = [
  { id: 900001, name: "Demo Strategic Suppliers", href: `${BASE}/portfolios/900001/`, companies: `${BASE}/companies/?portfolio=900001` },
  { id: 900002, name: "Demo Watchlist", href: `${BASE}/portfolios/900002/`, companies: `${BASE}/companies/?portfolio=900002` },
];
const portfolioMembers: Record<number, string[]> = {
  900001: companies.map((c) => c.id),
  900002: companies.filter((_, i) => i % 3 === 0).map((c) => c.id),
};

const outreaches: OutreachRequest[] = [
  demoOutreach("3", "Demo Private Supplier A", "DEMO-REF-003", false, "Request Created", null),
  demoOutreach("2", "Demo Private Supplier B", "DEMO-REF-002", false, "Awaiting Financials", null),
  demoOutreach("1", "Demo Private Supplier C", "DEMO-REF-001", true, "Rated", "DEMO004"),
];

function demoOutreach(id: string, name: string, ref: string, rated: boolean, status: string, rrid: string | null): OutreachRequest {
  const created = `2026-0${id}-10T09:00:00Z`;
  return {
    outreach_request_id: id,
    counterparty: {
      company_name: name,
      reference_id: ref,
      contact: { first_name: "Demo", last_name: "Contact", email: `contact${id}@example.invalid`, country: "USA", tel: null },
    },
    relationship_manager: { name: "Demo Manager", email: "manager@example.invalid" },
    created_at: created,
    modified_at: created,
    is_rated: rated,
    rrid,
    status,
    status_history: [{ status, status_updated_at: created, status_detail: null, status_comment: null, status_reasons: null }],
    financial_submissions: rated ? [{ created_at: created }] : [],
  };
}

const delay = () => new Promise((r) => setTimeout(r, 250));
const fail = (status: number, message: string, kind: ApiError["kind"]): never => {
  throw { kind, status, message } satisfies ApiError;
};

function paginate<T>(items: T[], params: Record<string, unknown>, pageSize: number, path: string): RridataPage<T> {
  const page = params.page === undefined ? 1 : Number(params.page);
  if (!Number.isInteger(page) || page < 1) fail(400, "Invalid parameter: page must to be a positive integer.", "bad-request");
  const pages = Math.max(1, Math.ceil(items.length / pageSize));
  // Provider behaviour: companies -> 404, portfolios/periods/ratings -> 500 for past-the-end pages
  if (page > pages) {
    if (path === "companies/") fail(404, "Not Found.", "not-found");
    fail(500, "Internal Server Error.", "server");
  }
  const link = (p: number) => `${BASE}/${path}?${new URLSearchParams({ ...stringify(params), page: String(p) })}`;
  return {
    count: items.length,
    previous: page > 1 ? link(page - 1) : null,
    next: page < pages ? link(page + 1) : null,
    results: items.slice((page - 1) * pageSize, page * pageSize),
  };
}

const stringify = (p: Record<string, unknown>) => Object.fromEntries(Object.entries(p).filter(([k]) => k !== "page").map(([k, v]) => [k, String(v)]));

function allowOnly(params: Record<string, unknown>, allowed: string[]) {
  for (const k of Object.keys(params)) if (!allowed.includes(k)) fail(400, `Not valid query parameter: ${k}.`, "bad-request");
}

export async function handleMockRequest<T>(host: HostKey, method: string, path: string, params: Record<string, unknown>, body?: unknown): Promise<T> {
  await delay();
  const clean = path.replace(/^\//, "");
  return (host === "data" ? dataRoute(method, clean, params) : outreachRoute(method, clean.replace(/\/$/, ""), params, body)) as T;
}

function dataRoute(method: string, path: string, params: Record<string, unknown>): unknown {
  if (method !== "GET") fail(405, "Method not allowed.", "bad-request");
  let m: RegExpMatchArray | null;

  if (path === "companies/") {
    allowOnly(params, ["page", "portfolio", "name", "ticker", "externalSourceId"]);
    let list = companies;
    if (params.portfolio !== undefined) {
      const members = portfolioMembers[Number(params.portfolio)];
      if (!members) fail(404, `Portfolio ${params.portfolio} not Found.`, "not-found");
      list = list.filter((c) => members.includes(c.id));
    }
    if (params.name !== undefined) {
      const name = String(params.name);
      if (name.length < 3) fail(400, "Invalid parameter: name must be at least 3 characters.", "bad-request");
      list = list.filter((c) => c.name.toLowerCase().includes(name.toLowerCase()));
    }
    if (params.ticker !== undefined) list = list.filter((c) => c.ticker === params.ticker);
    if (params.externalSourceId !== undefined) list = list.filter((c) => c.externalSourceId === params.externalSourceId);
    return paginate(list, params, 20, path);
  }
  if ((m = path.match(/^companies\/([^/]+)\/$/))) {
    const c = companies.find((x) => x.id === m![1]) ?? fail(404, "Not Found.", "not-found");
    return { ...c, reports: [], sectorAverages: { fhr: 50.0, chs: 50.0 } };
  }
  if ((m = path.match(/^companies\/([^/]+)\/(periods|ratings)\/$/))) {
    allowOnly(params, ["page"]);
    const idx = companies.findIndex((x) => x.id === m![1]);
    if (idx < 0) fail(404, "Not Found.", "not-found");
    const items = m[2] === "ratings" ? buildRatings(m[1], idx + 1) : buildPeriods(m[1], idx + 1);
    return paginate<Period | Rating>(items, params, 10, path);
  }
  if ((m = path.match(/^companies\/([^/]+)\/(periods|ratings)\/([^/]+)\/$/))) {
    const idx = companies.findIndex((x) => x.id === m![1]);
    if (idx < 0) fail(404, "Not Found.", "not-found");
    const items: (Period | Rating)[] = m[2] === "ratings" ? buildRatings(m[1], idx + 1) : buildPeriods(m[1], idx + 1);
    return items.find((x) => x.id === m![3]) ?? fail(404, "Not Found.", "not-found");
  }
  if (path === "portfolios/") {
    allowOnly(params, ["page"]);
    return paginate(portfolios, params, 20, path);
  }
  if ((m = path.match(/^portfolios\/([^/]+)\/$/))) {
    return portfolios.find((p) => String(p.id) === m![1]) ?? fail(404, `Portfolio ${m[1]} not Found.`, "not-found");
  }
  return fail(404, "Not Found.", "not-found");
}

function outreachRoute(method: string, path: string, params: Record<string, unknown>, body?: unknown): unknown {
  let m: RegExpMatchArray | null;
  if (path === "outreach-requests" && method === "GET") {
    allowOnly(params, ["limit", "cursor", "sort", "q", "embed"]);
    const limit = params.limit === undefined ? 20 : Number(params.limit);
    if (!Number.isInteger(limit)) fail(400, `invalid literal for int() with base 10: '${params.limit}'`, "bad-request");
    let list = [...outreaches];
    const q = new URLSearchParams(String(params.q ?? ""));
    const name = q.get("counterparty_company_name");
    const ref = q.get("reference_id");
    const rated = q.get("is_rated");
    if (name) list = list.filter((o) => o.counterparty.company_name.toLowerCase().includes(name.toLowerCase()));
    if (ref) list = list.filter((o) => (o.counterparty.reference_id ?? "").toLowerCase().includes(ref.toLowerCase()));
    if (rated) list = list.filter((o) => String(o.is_rated) === rated);
    const start = params.cursor ? Number(String(params.cursor).replace("demo-", "")) : 0;
    if (Number.isNaN(start)) fail(400, "Invalid Cursor", "bad-request");
    const items = list.slice(start, start + limit);
    const page: OutreachCursorPage<OutreachRequest> = {
      cursors: {
        query: { limit, sort: params.sort ?? "-id", ...(params.q ? { q: params.q } : {}) },
        total: list.length,
        ...(start + limit < list.length ? { next_cursor: `demo-${start + limit}` } : {}),
        ...(start > 0 ? { prev_cursor: `demo-${Math.max(0, start - limit)}` } : {}),
      },
      items,
    };
    return page;
  }
  if (path === "outreach-requests" && method === "POST") {
    const input = body as OutreachRequestCreate;
    const created: OutreachRequest = {
      ...input,
      outreach_request_id: String(outreaches.length + 1),
      created_at: new Date().toISOString(),
      modified_at: new Date().toISOString(),
      is_rated: false,
      rrid: null,
      status: "Request Created",
      status_history: [],
      financial_submissions: [],
    };
    outreaches.unshift(created);
    return created;
  }
  if ((m = path.match(/^outreach-requests\/([^/]+)\/communications$/))) {
    if (!outreaches.some((o) => o.outreach_request_id === m![1])) fail(404, "Solicitation matching query does not exist.", "not-found");
    return { communications: { introductions: [{ channel: "email", sent_at: "2026-01-11T09:00:00Z" }] } };
  }
  if ((m = path.match(/^outreach-requests\/([^/]+)$/))) {
    return outreaches.find((o) => o.outreach_request_id === m![1]) ?? fail(403, "Sorry, you do not have permission to access this resource", "forbidden");
  }
  return fail(404, "Not Found", "not-found");
}
