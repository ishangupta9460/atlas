import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import RecoveryPanel from "./RecoveryPanel";
import { Client } from "./api";
import { Workspace } from "./execution";

const data: Workspace = { serverTime: "2026-09-21T10:00:00Z", timezone: "UTC", tasks: [{ id: 1, title: "Study", completionCriterion: "Read", description: null, workState: "ready", completionPct: 20, importance: "medium", flexibilityTier: "flexible", deadline: null, goalId: 5, goalTitle: "Learn", goalState: "active", milestoneTitle: null, categoryName: null, blockers: 0 }], blocks: [{ id: 2, commitmentId: 1, startTime: "2026-09-21T09:00:00Z", endTime: "2026-09-21T10:00:00Z", state: "unresolved", placementReason: "Planned", sessionState: null, actualStart: null, runningSince: null, activeMillis: 0 }], fixed: [], history: [] };
const empty = { decisions: [], risks: [], recurring: [], reportedBlocks: [], claimedBlocks: [], patternStatus: "Unconfigured" };
function setup(state: object = empty, workspace = data) {
  const mock = vi.fn(async (path: string) => path === "/recovery/workspace" ? state : {});
  render(<RecoveryPanel client={mock as Client} data={workspace} refresh={vi.fn()} />);
  return mock;
}
describe("recovery choices", () => {
  it("surfaces a periodic batch suggestion without reactivating work", async () => {
    const mock = setup({ ...empty, deferredReview: { proposedAt: data.serverTime, reason: "Review these seven deferred tasks together." } });
    expect(await screen.findByText(/Review these seven deferred tasks together/)).toBeInTheDocument();
    expect(mock.mock.calls.filter(c => c[0].includes("reactivate"))).toHaveLength(0);
  });
  it("reports a recurring instance without fabricating session timestamps", async () => {
    const mock = setup({ ...empty, recurring: [{ id: 8, title: "Practice", target: 3, remaining: 3, goalId: null, flexibilityTier: "flexible" }] },
      { ...data, tasks: [], blocks: [{ ...data.blocks[0], recurringIntentionId: 8, commitmentId: 0 }] });
    fireEvent.change(await screen.findByLabelText("Recurring outcome"), { target: { value: "completed" } });
    fireEvent.change(screen.getByLabelText("What happened in this instance?"), { target: { value: "Practiced without timer" } });
    fireEvent.click(screen.getByText("Save recurring report"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/blocks/2/report", expect.objectContaining({ body: JSON.stringify({ outcome: "completed", report: "Practiced without timer", completionPct: 100 }) })));
  });
  it("reports forgotten work without providing fabricated timestamps", async () => {
    const mock = setup(); await waitFor(() => expect(mock).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText("What happened?"), { target: { value: "completed" } });
    fireEvent.change(screen.getByLabelText("Report"), { target: { value: "Read without timer" } });
    fireEvent.click(screen.getByText("Save report"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/blocks/2/report", expect.objectContaining({ method: "POST", body: JSON.stringify({ outcome: "completed", report: "Read without timer", completionPct: 100 }) })));
  });
  it("passes total effort for progress-based recovery", async () => {
    const mock = setup({ ...empty, reportedBlocks: [2] }); await screen.findByLabelText("Recovery estimate for Study");
    fireEvent.change(screen.getByLabelText("Recovery estimate for Study"), { target: { value: "60" } });
    fireEvent.click(screen.getByText("Find suitable time for selected work"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/recovery", expect.objectContaining({ body: JSON.stringify({ items: [{ blockId: 2, totalWorkMinutes: 60 }] }) })));
  });
  it("does not approve critical proposals on render", async () => {
    const mock = setup({ ...empty, decisions: [{ id: 7, state: "pending", proposal: { tier: "CRITICAL", reason: "Protected work needs your decision", deferred: [1], plan: { placements: [] } } }] });
    await screen.findByText("Your decision is needed");expect(mock.mock.calls.filter(c => c[0].includes("response"))).toHaveLength(0);
    fireEvent.click(screen.getByText("Approve recovery"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/recovery/7/response", expect.objectContaining({ body: '{"accept":true}' })));
  });
  it("shows five risk choices and keeps silence separate from resolution", async () => {
    const mock = setup({ ...empty, risks: [{ goalId: 5, awaitingResponse: true, planningState: "active", inputs: {}, calculation: { confidence: .79, threshold: .8, remainingMinutes: 100, capacityMinutes: 100, result: "at_risk" } }] });
    await screen.findByText(/Goal at risk/); expect(screen.getByLabelText("Choose a next step").querySelectorAll("option")).toHaveLength(5);
    expect(screen.getByText(/not paused or abandoned by silence/)).toBeInTheDocument();expect(mock.mock.calls.filter(c => c[0].includes("response"))).toHaveLength(0);
  });
  it("reviews deferred work as one batch", async () => {
    const mock = setup(empty, { ...data, tasks: [{ ...data.tasks[0], workState: "deferred" }] });
    fireEvent.click(screen.getByText("Return this batch to active planning"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/recovery/deferred/reactivate", expect.objectContaining({ body: "[1]" })));
  });
  it("requires explicit effort before recurring generation", async () => {
    setup({ ...empty, recurring: [{ id: 8, title: "Practice", target: 3, remaining: 3, goalId: null, flexibilityTier: "flexible" }] });
    await screen.findByText("3 remaining of 3 this week");expect(screen.getByText("Schedule this week's remaining target")).toBeDisabled();
  });
  it("offers recurring-only goals and keeps horizon effort separate from instance length", async () => {
    const mock = setup({ ...empty, goals: [{ id: 9, title: "Recurring goal" }], recurring: [{ id: 8, title: "Practice", target: 3, remaining: 3, goalId: 9, flexibilityTier: "flexible" }] }, { ...data, tasks: [], blocks: [] });
    await screen.findByRole("option", { name: "Recurring goal" });
    fireEvent.change(screen.getByLabelText("Goal"), { target: { value: "9" } });
    fireEvent.change(screen.getByLabelText(/remaining effort through the goal deadline/), { target: { value: "240" } });
    fireEvent.change(screen.getByLabelText("Minutes per instance"), { target: { value: "20" } });
    fireEvent.change(screen.getByLabelText("Intended local hour (0–23)"), { target: { value: "9" } });
    fireEvent.click(screen.getByText("Evaluate feasibility"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/goals/9/risk", expect.objectContaining({ body: JSON.stringify({ estimates: [], recurringEstimates: [{ recurringIntentionId: 8, remainingWorkMinutes: 240, localHour: 9 }] }) })));
    expect(screen.getByLabelText("Minutes per instance")).toHaveValue(20);
  });
  it("enables recurring start when the live clock enters the window", async () => {
    const mock = vi.fn(async () => ({ ...empty, recurring: [{ id: 8, title: "Practice", target: 3, remaining: 3, goalId: null, flexibilityTier: "flexible" }] }));
    const workspace = { ...data, tasks: [], blocks: [{ ...data.blocks[0], recurringIntentionId: 8, commitmentId: 0, state: "scheduled", startTime: "2026-09-21T10:01:00Z", endTime: "2026-09-21T10:31:00Z" }] };
    const props = { client: mock as Client, data: workspace, refresh: vi.fn() };
    const { rerender } = render(<RecoveryPanel {...props} now={Date.parse("2026-09-21T10:00:00Z")} />);
    expect(await screen.findByText("Start recurring session")).toBeDisabled();
    rerender(<RecoveryPanel {...props} now={Date.parse("2026-09-21T10:01:00Z")} />);
    expect(screen.getByText("Start recurring session")).toBeEnabled();
  });
});
