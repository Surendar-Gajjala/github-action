import { z } from "zod";
import { storage } from "@/utils/storage";
import type { ResourceKey } from "@/types/schema";

/** Non-sensitive demo schedule configuration. Nothing here triggers a run; it's configuration only. */
export const scheduleEntrySchema = z
  .object({
    resource: z.enum(["companies", "portfolios", "periods", "ratings", "outreaches"]),
    enabled: z.boolean(),
    frequency: z.enum(["hourly", "daily", "weekly"]),
    timezone: z.string().min(1, "Timezone is required"),
    time: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Use HH:MM (24h)"),
    dayOfWeek: z.coerce.number().int().min(0).max(6),
  })
  .refine((v) => isValidTimeZone(v.timezone), { message: "Unknown timezone", path: ["timezone"] });

export const scheduleSchema = z.object({ entries: z.array(scheduleEntrySchema) });
export type ScheduleEntry = z.infer<typeof scheduleEntrySchema>;
export type ScheduleConfig = z.infer<typeof scheduleSchema>;

const KEY = "rr.syncSchedule";
const RESOURCES: ResourceKey[] = ["companies", "portfolios", "periods", "ratings", "outreaches"];

export function loadSchedule(): ScheduleConfig {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  const parsed = scheduleSchema.safeParse(storage.get(KEY, null));
  if (parsed.success) return parsed.data;
  return { entries: RESOURCES.map((resource) => ({ resource, enabled: false, frequency: "daily", timezone: tz, time: "02:00", dayOfWeek: 1 })) };
}

export function saveSchedule(cfg: ScheduleConfig) {
  storage.set(KEY, cfg);
}

export function isValidTimeZone(tz: string) {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

/** Offset (ms) of `timeZone` from UTC at instant `date`. */
function tzOffset(date: Date, timeZone: string) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-US", { timeZone, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" })
      .formatToParts(date)
      .map((p) => [p.type, p.value]),
  );
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  return asUtc - date.getTime();
}

/** Wall-clock parts of `date` in `timeZone`. */
function zonedParts(date: Date, timeZone: string) {
  const shifted = new Date(date.getTime() + tzOffset(date, timeZone));
  return { y: shifted.getUTCFullYear(), m: shifted.getUTCMonth(), d: shifted.getUTCDate(), dow: shifted.getUTCDay() };
}

function zonedToUtc(y: number, m: number, d: number, h: number, min: number, timeZone: string) {
  const guess = Date.UTC(y, m, d, h, min);
  return new Date(guess - tzOffset(new Date(guess), timeZone));
}

/** Next planned run for display. A browser cannot execute it reliably; a backend scheduler must. */
export function nextPlannedRun(e: ScheduleEntry, now = new Date()): Date | undefined {
  if (!e.enabled || !isValidTimeZone(e.timezone) || !/^\d{2}:\d{2}$/.test(e.time)) return undefined;
  const [h, min] = e.time.split(":").map(Number);
  if (e.frequency === "hourly") {
    // Hourly runs use only the minute of `time`, evaluated in the target zone (handles :30/:45 offsets)
    for (let i = 0; i <= 60; i++) {
      const c = new Date(Math.ceil(now.getTime() / 60_000) * 60_000 + i * 60_000);
      const local = new Date(c.getTime() + tzOffset(c, e.timezone));
      if (local.getUTCMinutes() === min) return c;
    }
    return undefined;
  }
  const start = zonedParts(now, e.timezone);
  for (let i = 0; i <= 8; i++) {
    const day = new Date(Date.UTC(start.y, start.m, start.d + i));
    if (e.frequency === "weekly" && day.getUTCDay() !== e.dayOfWeek) continue;
    const candidate = zonedToUtc(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(), h, min, e.timezone);
    if (candidate > now) return candidate;
  }
  return undefined;
}
