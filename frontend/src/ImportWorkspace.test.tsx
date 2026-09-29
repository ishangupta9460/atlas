import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import ImportWorkspace, { ImportNode, ImportView } from "./ImportWorkspace";
import { Client } from "./api";

const node: ImportNode = { id: 1, parentId: null, type: "task", title: "Build parser", text: "- Build parser", included: true, completionCriterion: null, importance: null, flexibilityTier: null, resourceType: null, reference: null, resourceId: null, startTime: null, endTime: null, warning: null };
const draft = (): ImportView => ({ id: 1, kind: "roadmap", filename: "plan.md", state: "review", revision: 0, proposal: { nodes: [node, { ...node, id: 2, type: "optional", title: "Polish", included: false }], warning: null }, result: null, scheduledCount: 0 });
it.each([
  { kind: "roadmap", goalId: 10, opensGoal: true },
  { kind: "roadmap", goalId: undefined, opensGoal: false },
  { kind: "fixed", goalId: null, opensGoal: false },
] as const)("reloads historical $kind import with goalId=$goalId", async ({ kind, goalId, opensGoal }) => {
  const user = userEvent.setup();
  const view: ImportView = { ...draft(), kind, state: "approved", result: { roadmapId: kind === "roadmap" ? 7 : null, goalId, commitmentIds: [], resourceIds: [], fixedCommitmentIds: kind === "fixed" ? [42] : [], conflictingBlockIds: [] } };
  const call = vi.fn(async (path: string) => {
    if (path.startsWith("/goals")) return { goals: [] };
    if (path === "/resources") return [];
    if (path.endsWith("/import/1")) return view;
    return [view];
  });
  const onGoal = vi.fn();
  render(<ImportWorkspace client={call as Client} onWork={vi.fn()} onGoal={onGoal} />);
  if (kind === "fixed") await user.selectOptions(screen.getByLabelText("Import type"), "fixed");
  await screen.findByRole("option", { name: "plan.md · approved" });
  await user.selectOptions(screen.getByLabelText("Previous imports"), "1");
  await screen.findByText(/Approved \/ Imported/);
  expect(call).toHaveBeenCalledWith(kind === "fixed" ? "/fixed-commitments/import/1" : "/roadmaps/import/1");
  if (opensGoal) {
    await user.click(screen.getByRole("button", { name: "Open Goal" }));
    expect(onGoal).toHaveBeenCalledWith(10);
  } else {
    expect(screen.queryByRole("button", { name: "Open Goal" })).not.toBeInTheDocument();
    expect(onGoal).not.toHaveBeenCalled();
  }
});
it("keeps edits in review and only explicit approval imports work", async () => {
  const user = userEvent.setup(); let view = draft();
  const call = vi.fn(async (path: string, options?: RequestInit) => {
    if (path === "/goals?limit=100") return { goals: [{ id: 10, title: "Learn" }] };
    if (path === "/resources") return [];
    if (path === "/roadmaps/import" && !options?.method) return [];
    if (path === "/roadmaps/import" && options?.method === "POST") return view;
    if (options?.method === "PATCH") { const body = JSON.parse(String(options.body)); view = { ...view, revision: 1, proposal: { nodes: body.nodes, warning: null } }; return view; }
    if (path.endsWith("/approve")) { view = { ...view, state: "approved", result: { roadmapId: 7, goalId: 10, commitmentIds: [42], resourceIds: [], fixedCommitmentIds: [], conflictingBlockIds: [] } }; return view; }
    throw new Error(path);
  });
  const onWork = vi.fn(), onGoal = vi.fn(); render(<ImportWorkspace client={call as Client} onWork={onWork} onGoal={onGoal} />);
  await user.upload(screen.getByLabelText(/Markdown or text document/), new File(["- Build parser"], "plan.md", { type: "text/markdown" }));
  await user.click(screen.getByRole("button", { name: "Upload for review" }));
  expect(await screen.findByText(/Draft \/ Review/)).toBeInTheDocument();
  expect(screen.getByText(/Parsed: 1 task · 1 optional. Selected: 1 of 2/)).toBeInTheDocument();
  expect(call.mock.calls.some(([path]) => path.endsWith("/approve"))).toBe(false);
  await user.type(screen.getByLabelText("Completion criterion for #1"), "Parser tests pass");
  expect(screen.getByRole("button", { name: "Approve reviewed import" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "Save review edits" }));
  await user.selectOptions(screen.getByLabelText("Goal for this roadmap"), "10");
  await user.selectOptions(screen.getByLabelText("Task importance"), "medium");
  await user.selectOptions(screen.getByLabelText("Task flexibility"), "flexible");
  await user.click(screen.getByRole("button", { name: "Approve reviewed import" }));
  expect(await screen.findByText(/Approved \/ Imported/)).toBeInTheDocument();
  expect(screen.getByText("No work scheduled by this import.")).toBeInTheDocument();
  const approve = call.mock.calls.find(([path]) => path.endsWith("/approve"))!;
  expect(JSON.parse(String(approve[1]?.body))).toMatchObject({ revision: 1, goalId: 10 });
  await user.click(screen.getByRole("button", { name: /Open imported task #42/ })); expect(onWork).toHaveBeenCalledWith(42);
  await user.click(screen.getByRole("button", { name: "Open Goal" })); expect(onGoal).toHaveBeenCalledWith(10);
});
it("excludes sections and keeps failed approvals editable", async () => {
  const user = userEvent.setup(); const view = draft();
  const call = vi.fn(async (path: string, options?: RequestInit) => {
    if (path.startsWith("/goals")) return { goals: [{ id: 10, title: "Learn" }] };
    if (path === "/resources") return [];
    if (path === "/roadmaps/import") return [view];
    if (path.endsWith("/approve")) throw new Error("Review changed. Reload.");
    if (options?.method === "PATCH") return { ...view, revision: 1, proposal: { nodes: JSON.parse(String(options.body)).nodes, warning: null } };
    return view;
  });
  render(<ImportWorkspace client={call as Client} onWork={vi.fn()} onGoal={vi.fn()} />);
  await waitFor(() => expect(screen.getByRole("option", { name: "plan.md · review" })).toBeInTheDocument());
  await user.selectOptions(screen.getByLabelText("Previous imports"), "1");
  await user.click(await screen.findByLabelText("Include in import"));
  expect(screen.getByRole("button", { name: "Approve reviewed import" })).toBeDisabled();
  await user.click(screen.getByLabelText("Include in import")); await user.click(screen.getByRole("button", { name: "Save review edits" }));
  await user.selectOptions(screen.getByLabelText("Goal for this roadmap"), "10"); await user.selectOptions(screen.getByLabelText("Task importance"), "medium"); await user.selectOptions(screen.getByLabelText("Task flexibility"), "flexible");
  await user.click(screen.getByRole("button", { name: "Approve reviewed import" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Review changed");expect(screen.getByLabelText("Title for #1")).toBeEnabled();
});
it("screenshots use their own review flow and manual candidate entry", async () => {
  const user = userEvent.setup(); const call = vi.fn(async (path: string, options?: RequestInit) => {
    if (path.startsWith("/goals")) return { goals: [] }; if (path === "/resources") return [];
    if (options?.method === "POST") return { ...draft(), kind: "fixed", filename: "schedule.png", proposal: { nodes: [], warning: "Local OCR is unavailable." } };
    return [];
  });
  render(<ImportWorkspace client={call as Client} onWork={vi.fn()} onGoal={vi.fn()} />);
  await user.selectOptions(screen.getByLabelText("Import type"), "fixed");
  await user.upload(screen.getByLabelText(/PNG or JPEG/), new File(["image"], "schedule.png", { type: "image/png" }));await user.click(screen.getByRole("button", { name: "Upload for review" }));
  expect(await screen.findByText("Local OCR is unavailable.")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Add schedule entry" }));expect(screen.getByLabelText("Start with UTC offset for #1")).toBeInTheDocument();
  expect(call.mock.calls.some(([p]) => p.endsWith("/approve"))).toBe(false);
});
