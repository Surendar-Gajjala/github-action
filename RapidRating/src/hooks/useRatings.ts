import { useQuery } from "@tanstack/react-query";
import { ratingService } from "@/services/rapidRatingsRatingService";
import { getAuthMode } from "@/services/config";

/** Full rating history for a company (provider pages of 10 are followed via `next`). */
export function useRatings(rrid: string | undefined) {
  return useQuery({
    queryKey: ["ratings", getAuthMode(), rrid],
    queryFn: () => ratingService.listAll(rrid!),
    enabled: !!rrid,
  });
}
