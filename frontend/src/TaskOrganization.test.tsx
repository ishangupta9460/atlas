import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "./App";

const category = { id: 7, name: "Learning", color: "#285c45", defaultImportance: "high", defaultFlexibilityTier: "protected" };
const task = { id: 5, title: "Play a song", completionCriterion: "Play all verses", goalId: 1, milestoneId: null, categoryId: 7, importance: "medium", flexibilityTier: "flexible", workState: "ready" };
const prerequisite = { ...task, id: 8, title: "Learn chords", goalId: 2 };
type Handler = (body: Record<string, unknown>) => Response | Promise<Response>;
let routes: Record<string, Handler>;
const fetchMock = vi.fn();
const response = (body: unknown, status = 200) => new Response(status === 204 ? null : JSON.stringify(body), { status });
beforeEach(() => {
  sessionStorage.clear(); sessionStorage.setItem("atlas.session", "token");
  routes = {
    "GET /api/auth/me": () => response({ id: 1, email: "person@example.com" }),
    "GET /goals": () => response({ goals: [{ id: 1, title: "Music", lifecycleState: "active", planningState: "active" }], nextCursor: null }),
    "GET /goals/1/roadmap": () => response({ roadmap: null }),
    "GET /goals/1/commitments": () => response({ commitments: [task], nextCursor: null }),
    "GET /categories": () => response([category]),
    "GET /commitments/5/dependencies": () => response([]),
    "GET /commitments?q=&excludeId=5": () => response({ commitments: [prerequisite], nextCursor: null }),
  };
  fetchMock.mockReset(); fetchMock.mockImplementation(async (path: string, options: RequestInit = {}) => {
    const key = `${options.method ?? "GET"} ${path}`;
    if (!routes[key]) throw new Error(`Unexpected request: ${key}`);
    return routes[key](options.body ? JSON.parse(String(options.body)) : {});
  });
  vi.stubGlobal("fetch", fetchMock);
});
async function plan() {
  const user = userEvent.setup(); render(<App />);
  await user.click(await screen.findByRole("button", { name: "Open plan" }));
  await screen.findByText(task.title);
  return user;
}

it("creates a category inline and saves a task with its defaults without fabricating overrides", async () => {
  routes["GET /categories"] = () => response([]);
  routes["POST /categories"] = body => response({ ...category, ...body }, 201);
  routes["POST /commitments"] = body => response({ ...task, ...body, title: "Practice", importance: "high", flexibilityTier: "protected" }, 201);
  const user = await plan();
  await user.click(screen.getByRole("button", { name: "Add a task" }));
  await user.type(screen.getByLabelText("What will you do?"), "Practice");
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.click(screen.getByText("Category and defaults"));
  await user.click(screen.getByRole("button", { name: "Create a category" }));
  await user.type(screen.getByLabelText("What would you call this category?"), "Learning");
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.selectOptions(screen.getByLabelText("How flexible are tasks in this category?"), "protected");
  await user.selectOptions(screen.getByLabelText(/Usual importance/), "high");
  await user.click(screen.getByRole("button", { name: "Save category" }));
  await screen.findByText("Use category default (high)");
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  await screen.findByText("Practice");
  const call = fetchMock.mock.calls.find(([path, options]) => path === "/commitments" && options.method === "POST");
  expect(JSON.parse(call![1].body)).toEqual({ title: "Practice", completionCriterion: null, goalId: 1, milestoneId: null, categoryId: 7 });
});

it("keeps existing task choices when removing its category", async () => {
  routes["PATCH /commitments/5"] = body => response({ ...task, ...body });
  const user = await plan();
  await user.click(screen.getByRole("button", { name: `Edit ${task.title}` }));
  await user.click(screen.getByRole("button", { name: "Continue" }));
  expect(screen.getByLabelText("How important is this task?")).toHaveValue("medium");
  await user.selectOptions(screen.getByLabelText("Category"), "");
  await user.click(screen.getByRole("button", { name: "Save task" }));
  await screen.findByText("Task saved.");
  const call = fetchMock.mock.calls.find(([path, options]) => path === "/commitments/5" && options.method === "PATCH");
  expect(JSON.parse(call![1].body)).toMatchObject({ categoryId: null, importance: "medium", flexibilityTier: "flexible" });
});

it("requires importance when the chosen category has no default and permits explicit overrides", async () => {
  routes["GET /categories"] = () => response([{ ...category, defaultImportance: null }]);
  routes["POST /commitments"] = body => response({ ...task, ...body, title: "New task" }, 201);
  const user = await plan();
  await user.click(screen.getByRole("button", { name: "Add a task" }));
  await user.type(screen.getByLabelText("What will you do?"), "New task");
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.click(screen.getByText("Category and defaults"));
  await user.selectOptions(screen.getByLabelText("Category"), "7");
  expect(screen.getByRole("button", { name: "Save draft" })).toBeDisabled();
  await user.selectOptions(screen.getByLabelText("How important is this task?"), "critical");
  await user.selectOptions(screen.getByLabelText("How flexible is its placement?"), "optional");
  await user.click(screen.getByRole("button", { name: "Save draft" }));
  await screen.findByText("New task");
  const call = fetchMock.mock.calls.find(([path, options]) => path === "/commitments" && options.method === "POST");
  expect(JSON.parse(call![1].body)).toMatchObject({ categoryId: 7, importance: "critical", flexibilityTier: "optional" });
});

it("edits category defaults, reports in-use deletion, and handles a successful 204 removal", async () => {
  routes["PATCH /categories/7"] = body => response({ ...category, ...body });
  routes["DELETE /categories/7"] = () => response({ message: "Category is still used by a task" }, 409);
  const user = userEvent.setup(); render(<App />);
  await user.click(await screen.findByRole("button", { name: "Categories" }));
  await user.click(await screen.findByRole("button", { name: "Edit category Learning" }));
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.selectOptions(screen.getByLabelText(/Usual importance/), "");
  await user.click(screen.getByRole("button", { name: "Save category" }));
  await screen.findByText("Category saved. Existing tasks keep their importance and flexibility.");
  expect(screen.getByText(/Importance chosen per task/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Remove category Learning" }));
  await user.click(screen.getByRole("button", { name: "Confirm remove" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Category is still used");
  expect(screen.getByRole("heading", { name: "Learning" })).toBeInTheDocument();
  routes["DELETE /categories/7"] = () => response(null, 204);
  await user.click(screen.getByRole("button", { name: "Confirm remove" }));
  await screen.findByText("Category removed.");
  expect(screen.queryByRole("heading", { name: "Learning" })).not.toBeInTheDocument();
});

it("adds and reopens a cross-goal prerequisite and removes only its link", async () => {
  let linked = false;
  routes["GET /commitments/5/dependencies"] = () => response(linked ? [{ blockingCommitmentId: 8, blockedCommitmentId: 5 }] : []);
  routes["GET /commitments/8"] = () => response(prerequisite);
  routes["POST /commitments/5/dependencies"] = body => { expect(body).toEqual({ blockingCommitmentId: 8 }); linked = true; return response({ blockingCommitmentId: 8, blockedCommitmentId: 5 }, 201); };
  routes["DELETE /commitments/5/dependencies/8"] = () => { linked = false; return response(null, 204); };
  const user = await plan();
  await user.click(screen.getByRole("button", { name: `Prerequisites for ${task.title}` }));
  await user.click(await screen.findByRole("button", { name: "Add prerequisite Learn chords" }));
  await screen.findByText("Prerequisite added.");
  await user.click(screen.getByRole("button", { name: `Prerequisites for ${task.title}` }));
  await user.click(screen.getByRole("button", { name: `Prerequisites for ${task.title}` }));
  await user.click(await screen.findByRole("button", { name: "Remove prerequisite Learn chords" }));
  await screen.findByText("Prerequisite removed. Both tasks are still saved.");
  expect(screen.getByText(task.title)).toBeInTheDocument();
  expect(fetchMock.mock.calls.filter(([, options]) => options.method === "DELETE").map(([path]) => path)).toEqual(["/commitments/5/dependencies/8"]);
});

it("surfaces a cycle rejection without pretending the prerequisite was saved", async () => {
  routes["POST /commitments/5/dependencies"] = () => response({ message: "This dependency would create a cycle" }, 409);
  const user = await plan();
  await user.click(screen.getByRole("button", { name: `Prerequisites for ${task.title}` }));
  await user.click(await screen.findByRole("button", { name: "Add prerequisite Learn chords" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("would create a cycle");
  expect(screen.queryByRole("button", { name: "Remove prerequisite Learn chords" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Add prerequisite Learn chords" })).toBeEnabled();
});

it("searches and paginates prerequisites without discarding existing relationships", async () => {
  routes["GET /commitments/5/dependencies"] = () => response([{ blockingCommitmentId: 8, blockedCommitmentId: 5 }]);
  routes["GET /commitments/8"] = () => response(prerequisite);
  routes["GET /commitments?q=read&excludeId=5"] = () => response({ commitments: [{ ...prerequisite, id: 4, title: "Read notes" }], nextCursor: 4 });
  routes["GET /commitments?q=read&excludeId=5&cursor=4"] = () => response({ commitments: [{ ...prerequisite, id: 3, title: "Read rhythm" }], nextCursor: null });
  const user = await plan();
  await user.click(screen.getByRole("button", { name: `Prerequisites for ${task.title}` }));
  await screen.findByRole("button", { name: "Remove prerequisite Learn chords" });
  await user.type(screen.getByLabelText("Find a prerequisite"), "read");
  await user.click(screen.getByRole("button", { name: "Search tasks" }));
  await screen.findByText("Read notes");
  await user.click(screen.getByRole("button", { name: "More matching tasks" }));
  await screen.findByText("Read rhythm");
  expect(screen.getByRole("button", { name: "Remove prerequisite Learn chords" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "More matching tasks" })).not.toBeInTheDocument();
});

it("preserves failed prerequisite removal and clears private content on session expiry", async () => {
  routes["GET /commitments/5/dependencies"] = () => response([{ blockingCommitmentId: 8, blockedCommitmentId: 5 }]);
  routes["GET /commitments/8"] = () => response(prerequisite);
  routes["DELETE /commitments/5/dependencies/8"] = () => response({ message: "Connection interrupted" }, 503);
  const user = await plan();
  await user.click(screen.getByRole("button", { name: `Prerequisites for ${task.title}` }));
  await user.click(await screen.findByRole("button", { name: "Remove prerequisite Learn chords" }));
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "Remove prerequisite Learn chords" })).toBeEnabled();
  routes["DELETE /commitments/5/dependencies/8"] = () => response({ message: "Expired" }, 401);
  await user.click(screen.getByRole("button", { name: "Remove prerequisite Learn chords" }));
  await screen.findByText("Your session ended. Sign in to continue.");
  expect(screen.queryByText(task.title)).not.toBeInTheDocument();
  expect(sessionStorage.getItem("atlas.session")).toBeNull();
});
