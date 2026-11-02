import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { outreachService } from "@/services/rapidRatingsOutreachService";
import { getAuthMode } from "@/services/config";
import type { OutreachQuery, OutreachRequestCreate } from "@/types/outreach";

export function useOutreaches(query: OutreachQuery) {
  return useQuery({
    queryKey: ["outreaches", getAuthMode(), query],
    queryFn: () => outreachService.list(query),
    placeholderData: keepPreviousData,
  });
}

export function useOutreach(id: string | undefined) {
  return useQuery({
    queryKey: ["outreach", getAuthMode(), id],
    queryFn: () => outreachService.get(id!),
    enabled: !!id,
  });
}

export function useOutreachCommunications(id: string | undefined) {
  return useQuery({
    queryKey: ["outreach-comms", getAuthMode(), id],
    queryFn: () => outreachService.communications(id!),
    enabled: !!id,
  });
}

export function useCreateOutreach() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: OutreachRequestCreate) => outreachService.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["outreaches"] }),
  });
}
