// Display and explicit local-input conversion only; persisted timestamps stay UTC.
const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
export const displayZone = (zone?: string | null) => zone ?? browserZone;
const dateFormats = new Map<string, Intl.DateTimeFormat>();
const displayFormats = new Map<string, Intl.DateTimeFormat>();
const starts = new Map<string, number>();
export function formatInZone(value: string, zone: string | null | undefined, kind: "time" | "date" | "slot") {
  const resolved = displayZone(zone), key = `${resolved}:${kind}`;
  if (!displayFormats.has(key)) displayFormats.set(key, new Intl.DateTimeFormat(undefined, {
    timeZone: resolved,
    ...(kind === "date" ? { weekday: "short", month: "short", day: "numeric" } as const
      : { hour: "numeric", minute: "2-digit", ...(kind === "slot" ? { timeZoneName: "shortOffset" } as const : {}) } as const),
  }));
  return displayFormats.get(key)!.format(new Date(value));
}
export function dateKey(value: number, zone?: string | null) {
  const resolved = displayZone(zone);
  if (!dateFormats.has(resolved)) dateFormats.set(resolved, new Intl.DateTimeFormat("en-CA", { timeZone: resolved, year: "numeric", month: "2-digit", day: "2-digit" }));
  const parts = dateFormats.get(resolved)!.formatToParts(value);
  return ["year", "month", "day"].map(type => parts.find(p => p.type === type)!.value).join("-");
}
export function addDays(day: string, amount: number) {
  const value = new Date(`${day}T12:00:00Z`); value.setUTCDate(value.getUTCDate() + amount);
  return value.toISOString().slice(0, 10);
}
export function dayStart(day: string, zone?: string | null) {
  const cacheKey = `${displayZone(zone)}:${day}`;
  const cached = starts.get(cacheKey);
  if (cached !== undefined) return cached;
  const nominal = Date.parse(`${day}T00:00:00Z`);
  let low = nominal - 48 * 3600000, high = nominal + 48 * 3600000;
  while (high - low > 1) {
    const middle = Math.floor((low + high) / 2);
    if (dateKey(middle, zone) < day) low = middle; else high = middle;
  }
  if (starts.size > 2000) starts.clear();
  starts.set(cacheKey, high);
  return high;
}
export function localInput(value: string, zone?: string | null) {
  const nominal = Date.parse(`${value}Z`);
  if (!Number.isFinite(nominal)) return NaN;
  const format = new Intl.DateTimeFormat("sv-SE", { timeZone: displayZone(zone), year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23" });
  const offsets = new Set<number>();
  for (let h = -36; h <= 36; h += 6) {
    const sample = nominal + h * 3600000;
    offsets.add(Date.parse(format.format(sample).replace(" ", "T") + "Z") - sample);
  }
  const matches = [...offsets].map(offset => nominal - offset).filter(candidate => format.format(candidate).replace(" ", "T").slice(0, 16) === value);
  return matches.length === 1 ? matches[0] : NaN;
}
