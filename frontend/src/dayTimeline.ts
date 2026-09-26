import { Workspace } from "./execution";

export type DayItem = { key: string; title: string; start: number; end: number; kind: "work" | "fixed"; state: string; taskId: number | null; flexibility: string };
export function timelineItems(data: Workspace): DayItem[] {
  return [
    ...data.blocks.filter(b => b.state !== "superseded").map(b => ({
      key: `work-${b.id}`, title: data.tasks.find(t => t.id === b.commitmentId)?.title ?? "Untitled task",
      start: Date.parse(b.startTime), end: Date.parse(b.endTime), kind: "work" as const,
      state: b.state, taskId: b.commitmentId, flexibility: data.tasks.find(t => t.id === b.commitmentId)?.flexibilityTier ?? "",
    })),
    ...data.fixed.map(f => ({ key: `fixed-${f.id}`, title: f.title, start: Date.parse(f.startTime), end: Date.parse(f.endTime), kind: "fixed" as const, state: "fixed", taskId: null, flexibility: "fixed" })),
  ].sort((a, b) => a.start - b.start || a.key.localeCompare(b.key));
}
export function dayBounds(value: number) {
  const start = new Date(value); start.setHours(0, 0, 0, 0);
  const end = new Date(start); end.setDate(end.getDate() + 1);
  return { start: start.getTime(), end: end.getTime() };
}
export function dayItems(items: DayItem[], day: number) {
  const { start, end } = dayBounds(day);
  return items.filter(item => item.start < end && item.end > start);
}
// Gaps describe recorded placement only, never inferred availability or automatic buffers.
export function gapsBetween(items: DayItem[], start: number, end: number) {
  const gaps: { start: number; end: number }[] = [];
  let cursor = start;
  for (const item of [...items].sort((a, b) => a.start - b.start)) {
    if (item.end <= start || item.start >= end) continue;
    if (item.start > cursor) gaps.push({ start: cursor, end: Math.min(item.start, end) });
    cursor = Math.max(cursor, Math.min(item.end, end));
  }
  if (cursor < end) gaps.push({ start: cursor, end });
  return gaps;
}
export function itemStatus(item: DayItem, now: number) {
  if (item.state === "completed") return "Session finished";
  if (item.state === "active") return "In progress";
  if (item.kind === "fixed") return item.start <= now && item.end > now ? "Fixed · now" : "Fixed";
  if (item.end <= now) return "Pick up again";
  return item.start <= now ? "Ready now" : "Upcoming";
}
