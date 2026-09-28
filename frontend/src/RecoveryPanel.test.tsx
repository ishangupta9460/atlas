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
  it("reports that unconfigured production patterns await approved policy", async () => {
    setup({ ...empty, patterns: { configured: false, observations: [] } });
    expect(await screen.findByText("Production pattern observations are awaiting approved evidence policy.")).toBeInTheDocument();
  });
  it("shows only overlapping unstarted work, updates boundaries, and submits only affected blocks in the scheduling timezone", async () => {
    const tasks = ["Before", "Overlap", "After", "Active", "Completed"].map((title, i) => ({ ...data.tasks[0], id: i + 1, title, workState: i === 4 ? "completed" : "ready" }));
    const blocks = tasks.map((t, i) => ({ ...data.blocks[0], id: i + 10, commitmentId: t.id, state: i === 3 ? "active" : "scheduled",
      startTime: `2026-09-21T${i === 0 ? "09" : i === 2 ? "11" : "10"}:00:00Z`, endTime: `2026-09-21T${i === 0 ? "10" : i === 2 ? "12" : "11"}:00:00Z` }));
    blocks.push({ ...blocks[1], id: 99, commitmentId: 0 }); // Recurring windows do not take task estimates.
    const mock = setup(empty, { ...data, timezone: "Asia/Kolkata", tasks, blocks });
    const start = screen.getByLabelText("Unavailable from"), end = screen.getByLabelText("Until");
    expect(screen.queryByLabelText(/Total estimate for/)).not.toBeInTheDocument();
    fireEvent.change(start, { target: { value: "2026-09-21T15:30" } });
    expect(screen.queryByLabelText(/Total estimate for/)).not.toBeInTheDocument();
    fireEvent.change(end, { target: { value: "2026-09-21T16:30" } });
    expect(screen.getAllByLabelText(/Total estimate for/)).toHaveLength(1);
    fireEvent.change(screen.getByLabelText("Total estimate for Overlap (minutes)"), { target: { value: "30" } });
    expect(screen.queryByLabelText("Total estimate for Before (minutes)")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Total estimate for After (minutes)")).not.toBeInTheDocument();
    fireEvent.change(end, { target: { value: "2026-09-21T17:30" } });
    expect(screen.getByLabelText("Total estimate for After (minutes)")).toBeInTheDocument();
    fireEvent.change(start, { target: { value: "2026-09-21T16:30" } });
    expect(screen.queryByLabelText("Total estimate for Overlap (minutes)")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Total estimate for After (minutes)"), { target: { value: "45" } });
    fireEvent.click(screen.getByText("Record interruption"));
    await waitFor(() => expect(mock).toHaveBeenCalledWith("/recovery/interruption", expect.objectContaining({ body: JSON.stringify({ startTime: "2026-09-21T11:00:00.000Z", endTime: "2026-09-21T12:00:00.000Z", items: [{ blockId: 12, totalWorkMinutes: 45 }] }) })));
    fireEvent.change(end, { target: { value: "2026-09-21T15:30" } });
    expect(screen.queryByLabelText(/Total estimate for/)).not.toBeInTheDocument();
    fireEvent.submit(start.closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Choose a valid, unambiguous interval");
    expect(mock.mock.calls.filter(c => c[0] === "/recovery/interruption")).toHaveLength(1);
  });
  it.each(["2026-03-08T02:30", "2026-11-01T01:30"])("handles nonexistent or ambiguous local time %s without affected fields", value => {
    setup(empty, { ...data, timezone: "America/New_York" });
    fireEvent.change(screen.getByLabelText("Unavailable from"), { target: { value } });
    fireEvent.change(screen.getByLabelText("Until"), { target: { value: value.slice(0, 10) + "T04:00" } });
    expect(screen.queryByLabelText(/Total estimate for/)).not.toBeInTheDocument();
    expect(screen.getByText("Enter a valid, unambiguous interval to see affected work.")).toBeInTheDocument();
  });
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
