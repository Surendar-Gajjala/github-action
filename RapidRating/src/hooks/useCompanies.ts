import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { companyService } from "@/services/rapidRatingsCompanyService";
import { getAuthMode } from "@/services/config";
import type { CompanyQuery } from "@/types/company";

export function useCompanies(query: CompanyQuery, enabled = true) {
  return useQuery({
    queryKey: ["companies", getAuthMode(), query],
    queryFn: () => companyService.list(query),
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useCompany(rrid: string | undefined) {
  return useQuery({
    queryKey: ["company", getAuthMode(), rrid],
    queryFn: () => companyService.get(rrid!),
    enabled: !!rrid,
  });
}

/** All companies (follows `next`), used by the company selectors. */
export function useAllCompanies(enabled = true) {
  return useQuery({
    queryKey: ["companies-all", getAuthMode()],
    queryFn: () => companyService.listAll(),
    staleTime: 5 * 60_000,
    enabled,
  });
}
