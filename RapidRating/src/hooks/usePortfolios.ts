import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { portfolioService } from "@/services/rapidRatingsPortfolioService";
import { getAuthMode } from "@/services/config";

export function usePortfolios(page: number) {
  return useQuery({
    queryKey: ["portfolios", getAuthMode(), page],
    queryFn: () => portfolioService.list(page),
    placeholderData: keepPreviousData,
  });
}

export function usePortfolio(id: number | undefined) {
  return useQuery({
    queryKey: ["portfolio", getAuthMode(), id],
    queryFn: () => portfolioService.get(id!),
    enabled: id !== undefined,
  });
}
