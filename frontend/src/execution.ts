import { dateKey, formatInZone } from "./executionTime";
export type ExecutionView = "today" | "focus" | "schedule" | "progress";
export type Work = {
  id: number; title: string | null; completionCriterion: string | null; description: string | null;
  workState: string; completionPct: number; importance: string; flexibilityTier: string; deadline: string | null;
  goalId: number | null; goalTitle: string | null; goalState: string | null;
  milestoneTitle: string | null; categoryName: string | null; blockers: number; hardConsequence?: boolean;
};
export type Block = {
  id: number; commitmentId: number; startTime: string; endTime: string; state: string; placementReason: string;
  sessionState: "running" | "paused" | "finished" | null; actualStart: string | null; runningSince: string | null; activeMillis: number;
  userMovedFlag?: boolean; overrunPromptedAt?: string | null; recurringIntentionId?: number | null; title?: string;
};
export type History = {
  blockId: number; commitmentId: number; title: string; startTime: string; endTime: string;
  activeMillis: number; report: string; completionPct: number;
};
export type Workspace = {
  serverTime: string; tasks: Work[]; blocks: Block[];
  fixed: { id: number; title: string; startTime: string; endTime: string }[]; history: History[];
  timezone?: string | null;
  riskGoalIds?: number[];
  progress?: { plannedMillis: number; executedMillis: number; achieved: { commitmentId: number; goalId: number | null; title: string | null; completionPct: number }[] };
};
export const label = (s: string) => s.replace(/_/g, " ");
export const time = (s: string, zone?: string | null) => formatInZone(s, zone, "time");
export const date = (s: string, zone?: string | null) => formatInZone(s, zone, "date");
export const sameDay = (a: string, b: number, zone?: string | null) => dateKey(Date.parse(a), zone) === dateKey(b, zone);
export const duration = (ms: number) => {
  const minutes = Math.floor(Math.max(0, ms) / 60000);
  return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60}m` : `${minutes}m`;
};
export const elapsed = (b: Block, now: number) => b.activeMillis + (b.runningSince ? Math.max(0, now - Date.parse(b.runningSince)) : 0);
export const clock = (ms: number) => {
  const seconds = Math.floor(Math.max(0, ms) / 1000);
  return `${Math.floor(seconds / 3600).toString().padStart(2, "0")}:${Math.floor(seconds / 60 % 60).toString().padStart(2, "0")}:${(seconds % 60).toString().padStart(2, "0")}`;
};
