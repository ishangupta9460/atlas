import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "./App";

const fetchMock = vi.fn();
const goal = { id: 1, title: "Learn piano", lifecycleState: "active", planningState: "active" };
const milestone = { id: 3, title: "First song", order: 1 };
const task = { id: 5, title: "Practice a verse", completionCriterion: null, goalId: 1, milestoneId: 3, importance: "high", flexibilityTier: "flexible", workState: "draft" };
const reply = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
function enqueue(...bodies: unknown[]) { bodies.forEach(body => fetchMock.mockResolvedValueOnce(reply(body))); }
beforeEach(() => {
  sessionStorage.clear(); sessionStorage.setItem("atlas.session", "token"); fetchMock.mockReset();
  vi.stubGlobal("fetch", (path: string, options: RequestInit) => path === "/execution" ? Promise.resolve(reply({ serverTime: new Date().toISOString(), tasks: [], blocks: [], fixed: [], history: [] })) : fetchMock(path, options));
  enqueue({ id: 1, email: "person@example.com" }, { goals: [goal], nextCursor: null });
});
async function openPlan() {
  const user = userEvent.setup(); render(<App />);
  await user.click(await screen.findByRole("button", { name: "Goals" }));
  await user.click(await screen.findByRole("button", { name: "Open plan" }));
  await screen.findByRole("heading", { name: "Actionable tasks" });
  return user;
}

it("captures a goal task with only a title and offers the next readiness step", async () => {
  enqueue({ roadmap: null }, { commitments: [], nextCursor: null }, [], { ...task, milestoneId: null, importance: "medium", flexibilityTier: "flexible" });
  const user = await openPlan();
  await user.click(screen.getByRole("button", { name: "Add a task" }));
  await user.type(screen.getByLabelText("What will you do?"), task.title);
  await user.keyboard("{Enter}");
  expect(await screen.findByRole("button", { name: "Make ready →" })).toBeEnabled();
  expect(JSON.parse(fetchMock.mock.calls.find(([path, options]) => path === "/commitments" && options.method === "POST")![1].body)).toEqual({ title: task.title, completionCriterion: null, goalId: 1, importance: "medium", flexibilityTier: "flexible" });
});

it("preserves choices when returning from optional details to quick capture", async () => {
  enqueue({ roadmap: null }, { commitments: [], nextCursor: null }, [], task);
  const user = await openPlan();
  await user.click(screen.getByRole("button", { name: "Add a task" }));
  await user.type(screen.getByLabelText("What will you do?"), task.title);
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.selectOptions(screen.getByLabelText("How important is this task?"), "high");
  await user.selectOptions(screen.getByLabelText("How flexible is its placement?"), "protected");
  await user.click(screen.getByRole("button", { name: "Back" }));
  await user.click(screen.getByRole("button", { name: "Capture task" }));
  await screen.findByRole("button", { name: "Make ready →" });
  expect(JSON.parse(fetchMock.mock.calls.find(([path, options]) => path === "/commitments" && options.method === "POST")![1].body)).toMatchObject({ importance: "high", flexibilityTier: "protected" });
});

it("creates a milestone and a draft task, then defines done and reopens the saved plan", async () => {
  enqueue({ roadmap: null }, { commitments: [], nextCursor: null }, [], { id: 2, milestones: [] }, milestone, task);
  const user = await openPlan();
  await user.type(screen.getByLabelText("What is a useful milestone?"), milestone.title);
  await user.click(screen.getByRole("button", { name: "Add milestone" }));
  await screen.findByText("Milestone saved.");
  await user.click(screen.getByRole("button", { name: "Add a task" }));
  await user.type(screen.getByLabelText("What will you do?"), task.title);
  await user.click(screen.getByRole("button", { name: "Continue" }));
  expect(screen.getByRole("button", { name: "Save draft" })).toBeDisabled();
  await user.selectOptions(screen.getByLabelText("How important is this task?"), "high");
  await user.selectOptions(screen.getByLabelText("How flexible is its placement?"), "flexible");
  await user.click(screen.getByText("Place under a milestone"));
  await user.selectOptions(screen.getByLabelText("Milestone"), "3");
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  await screen.findByText("Draft saved. You can define done when you're ready.");
  expect(JSON.parse(fetchMock.mock.calls[7][1].body)).toEqual({ title: task.title, completionCriterion: null, goalId: 1, milestoneId: 3, importance: "high", flexibilityTier: "flexible" });
  const ready = { ...task, completionCriterion: "Play the verse without stopping", workState: "ready" };
  enqueue(ready);
  await user.click(screen.getByRole("button", { name: `Edit ${task.title}` }));
  await user.type(screen.getByLabelText(/What will done look like/), ready.completionCriterion);
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.click(screen.getByRole("button", { name: "Save task" }));
  await screen.findByText("ready");
  await user.click(screen.getByRole("button", { name: /All goals/ }));
  enqueue({ roadmap: { id: 2, milestones: [milestone] } }, { commitments: [ready], nextCursor: null }, []);
  await user.click(screen.getByRole("button", { name: "Open plan" }));
  await screen.findByText(`Done when: ${ready.completionCriterion}`);
  expect(fetchMock.mock.calls.filter(([path]) => path === "/goals/1/roadmaps")).toHaveLength(1);
});

it("retains the roadmap and milestone input when the first milestone save fails", async () => {
  enqueue({ roadmap: null }, { commitments: [], nextCursor: null }, [], { id: 2, milestones: [] });
  fetchMock.mockResolvedValueOnce(reply({ message: "Save interrupted" }, 500)); enqueue(milestone);
  const user = await openPlan();
  await user.type(screen.getByLabelText("What is a useful milestone?"), milestone.title);
  await user.click(screen.getByRole("button", { name: "Add milestone" }));
  await screen.findByRole("alert");
  expect(screen.getByLabelText("What is a useful milestone?")).toHaveValue(milestone.title);
  await user.click(screen.getByRole("button", { name: "Add milestone" }));
  await screen.findByText("Milestone saved.");
  expect(fetchMock.mock.calls.filter(([path]) => path === "/goals/1/roadmaps")).toHaveLength(1);
});

it("renames milestones and paginates tasks", async () => {
  enqueue({ roadmap: { id: 2, milestones: [milestone] } }, { commitments: [task], nextCursor: 5 }, [], { ...milestone, title: "First performance" }, { commitments: [{ ...task, id: 4, title: "Choose a song", milestoneId: null }], nextCursor: null });
  const user = await openPlan();
  await user.click(screen.getByRole("button", { name: "Rename First song" }));
  await user.clear(screen.getByLabelText("Milestone name")); await user.type(screen.getByLabelText("Milestone name"), "First performance");
  await user.click(screen.getByRole("button", { name: "Save milestone" }));
  await screen.findByText("Milestone updated.");
  await user.click(screen.getByRole("button", { name: "Show more tasks" }));
  await screen.findByText("Choose a song");
  expect(fetchMock.mock.calls[6][0]).toBe("/goals/1/commitments?cursor=5");
  expect(screen.queryByRole("button", { name: "Show more tasks" })).not.toBeInTheDocument();
});

it("keeps failed task edits and prevents clearing the defining fields of ready work", async () => {
  const ready = { ...task, completionCriterion: "Play a verse", workState: "ready" };
  enqueue({ roadmap: { id: 2, milestones: [milestone] } }, { commitments: [ready], nextCursor: null }, []);
  fetchMock.mockResolvedValueOnce(reply({ message: "Could not save task" }, 500)); enqueue({ ...ready, title: "Practice slowly" });
  const user = await openPlan();
  await user.click(screen.getByRole("button", { name: `Edit ${task.title}` }));
  expect(screen.getByLabelText(/What will done look like/)).toBeRequired();
  await user.clear(screen.getByLabelText("What will you do?")); await user.type(screen.getByLabelText("What will you do?"), "Practice slowly");
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.click(screen.getByRole("button", { name: "Save task" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Could not save task");
  await user.click(screen.getByRole("button", { name: "Save task" }));
  await screen.findByText("Practice slowly");
});

it("shows a retry after a failed plan read without presenting an empty editable plan", async () => {
  fetchMock.mockResolvedValueOnce(reply({ message: "Plan unavailable" }, 503)); enqueue({ commitments: [], nextCursor: null }, []);
  const user = userEvent.setup(); render(<App />);
  await user.click(await screen.findByRole("button", { name: "Goals" }));
  await user.click(await screen.findByRole("button", { name: "Open plan" }));
  await screen.findByRole("alert");
  expect(screen.queryByRole("button", { name: "Add a task" })).not.toBeInTheDocument();
  enqueue({ roadmap: null }, { commitments: [], nextCursor: null }, []);
  await user.click(screen.getByRole("button", { name: "Reload plan" }));
  await screen.findByRole("button", { name: "Add a task" });
});

it("clears the plan on session expiry during a save", async () => {
  enqueue({ roadmap: { id: 2, milestones: [milestone] } }, { commitments: [task], nextCursor: null }, []);
  fetchMock.mockResolvedValueOnce(reply({ message: "Expired" }, 401));
  const user = await openPlan();
  await user.type(screen.getByLabelText("What is a useful milestone?"), "Private step");
  await user.click(screen.getByRole("button", { name: "Add milestone" }));
  await screen.findByText("Your session ended. Sign in to continue.");
  expect(screen.queryByText(task.title)).not.toBeInTheDocument();
  await waitFor(() => expect(sessionStorage.getItem("atlas.session")).toBeNull());
});
