import { useQuery } from "@tanstack/react-query";
import { periodService } from "@/services/rapidRatingsPeriodService";
import { getAuthMode } from "@/services/config";

/** All periods for a company (provider pages of 10 are followed via `next`; ~3 pages per company). */
export function usePeriods(rrid: string | undefined) {
  return useQuery({
    queryKey: ["periods", getAuthMode(), rrid],
    queryFn: () => periodService.listAll(rrid!),
    enabled: !!rrid,
  });
}
