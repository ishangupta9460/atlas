import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import App from "./App";

const fetchMock = vi.fn();
const profile = { id: 1, email: "person@example.com" };
const goal = { id: 1, title: "Learn piano", description: null, targetDeadline: null, lifecycleState: "active", planningState: "active" };
function reply(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }); }
function enqueue(...bodies: unknown[]) { bodies.forEach(body => fetchMock.mockResolvedValueOnce(reply(body))); }
function signedIn() { sessionStorage.setItem("atlas.session", "test-token"); enqueue(profile); }
beforeEach(() => { sessionStorage.clear(); localStorage.clear(); fetchMock.mockReset(); vi.stubGlobal("fetch", fetchMock); });

it("registers, signs in, captures a goal, edits its date, and signs out", async () => {
  const user = userEvent.setup();
  enqueue(profile, { token: "test-token" }, profile, { goals: [], nextCursor: null }, goal, { ...goal, targetDeadline: "2027-04-05" });
  render(<App />);
  await user.click(screen.getByRole("button", { name: "New here? Create an account" }));
  await user.type(screen.getByLabelText("Email"), profile.email);
  await user.type(screen.getByLabelText("Password"), "password123");
  await user.click(screen.getByRole("button", { name: "Create account" }));
  await screen.findByText("A little intention goes a long way.");
  await user.type(screen.getByLabelText("Your next goal"), goal.title);
  await user.click(screen.getByRole("button", { name: "Save goal" }));
  await screen.findByDisplayValue(goal.title);
  const date = screen.getByLabelText(/Is there a target date/) as HTMLInputElement;
  await user.type(date, "2027-04-05");
  await user.click(screen.getByRole("button", { name: "Save changes" }));
  await screen.findByText("Target date · 2027-04-05");
  expect(JSON.parse(fetchMock.mock.calls[5][1].body)).toEqual({ title: goal.title, targetDeadline: "2027-04-05" });
  expect(fetchMock.mock.calls[3][1].headers.Authorization).toBe("Bearer test-token");
  await user.click(screen.getByRole("button", { name: "Sign out" }));
  expect(screen.queryByText(goal.title)).not.toBeInTheDocument();
  expect(sessionStorage.getItem("atlas.session")).toBeNull();
});

it("restores an authenticated session and paginates saved goals", async () => {
  signedIn(); enqueue({ goals: [goal], nextCursor: 1 }, { goals: [{ ...goal, id: 2, title: "Read more" }], nextCursor: null });
  const user = userEvent.setup(); render(<App />);
  await screen.findByText(goal.title);
  await user.click(screen.getByRole("button", { name: "Show more goals" }));
  await screen.findByText("Read more");
  expect(fetchMock.mock.calls[2][0]).toBe("/goals?cursor=1");
  expect(screen.queryByRole("button", { name: "Show more goals" })).not.toBeInTheDocument();
});

it("reports incorrect credentials and allows retry without losing the email", async () => {
  fetchMock.mockResolvedValueOnce(reply({ message: "Invalid email or password" }, 401));
  const user = userEvent.setup(); render(<App />);
  await user.type(screen.getByLabelText("Email"), profile.email);
  await user.type(screen.getByLabelText("Password"), "wrong");
  await user.click(screen.getByRole("button", { name: "Sign in" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
  expect(screen.getByLabelText("Email")).toHaveValue(profile.email);
  expect(screen.getByRole("button", { name: "Sign in" })).toBeEnabled();
});

it("keeps registration success explicit if automatic login fails", async () => {
  enqueue(profile); fetchMock.mockResolvedValueOnce(reply({ message: "Try again" }, 503));
  const user = userEvent.setup(); render(<App />);
  await user.click(screen.getByRole("button", { name: "New here? Create an account" }));
  await user.type(screen.getByLabelText("Email"), profile.email);
  await user.type(screen.getByLabelText("Password"), "password123");
  await user.click(screen.getByRole("button", { name: "Create account" }));
  await screen.findByText("Your account is ready. Sign in to continue.");
  expect(screen.getByRole("button", { name: "Sign in" })).toBeEnabled();
});

it("clears private content and the saved token when a protected request expires", async () => {
  signedIn(); enqueue({ goals: [goal], nextCursor: null });
  fetchMock.mockResolvedValueOnce(reply({ message: "Authentication required" }, 401));
  const user = userEvent.setup(); render(<App />);
  await screen.findByText(goal.title);
  await user.click(screen.getByRole("button", { name: "Tasks" }));
  await screen.findByText("Your session ended. Sign in to continue.");
  expect(screen.queryByText(goal.title)).not.toBeInTheDocument();
  expect(sessionStorage.getItem("atlas.session")).toBeNull();
});

it("distinguishes a connection failure from expired credentials during restore", async () => {
  sessionStorage.setItem("atlas.session", "test-token");
  fetchMock.mockRejectedValueOnce(new TypeError("offline"));
  const user = userEvent.setup(); render(<App />);
  expect(await screen.findByRole("alert")).toHaveTextContent("couldn't connect");
  expect(sessionStorage.getItem("atlas.session")).toBe("test-token");
  enqueue(profile, { goals: [goal], nextCursor: null });
  await user.click(screen.getByRole("button", { name: "Try again" }));
  await screen.findByText(goal.title);
});

it("runs the existing task create, start, finish loop", async () => {
  signedIn(); enqueue({ goals: [], nextCursor: null }, [], { id: 1, title: "Read a chapter", status: "ready" }, { id: 1, title: "Read a chapter", status: "in_progress" }, { id: 1, title: "Read a chapter", status: "completed" });
  const user = userEvent.setup(); render(<App />);
  await screen.findByText("A little intention goes a long way.");
  await user.click(screen.getByRole("button", { name: "Tasks" }));
  await screen.findByText("A clear space.");
  await user.type(screen.getByLabelText("What needs doing?"), "Read a chapter");
  await user.click(screen.getByRole("button", { name: "Add task" }));
  await user.click(await screen.findByRole("button", { name: "Start" }));
  await user.click(await screen.findByRole("button", { name: "Finish" }));
  await screen.findByText("Finished. A little more progress made.");
  expect(screen.queryByText("Read a chapter")).not.toBeInTheDocument();
});

it("retains goal input and allows retry when saving fails", async () => {
  signedIn(); enqueue({ goals: [], nextCursor: null });
  fetchMock.mockResolvedValueOnce(reply({ message: "Could not save" }, 500)); enqueue(goal);
  const user = userEvent.setup(); render(<App />);
  await screen.findByText("A little intention goes a long way.");
  await user.type(screen.getByLabelText("Your next goal"), goal.title);
  await user.click(screen.getByRole("button", { name: "Save goal" }));
  await screen.findByRole("alert");
  expect(screen.getByLabelText("Your next goal")).toHaveValue(goal.title);
  await user.click(screen.getByRole("button", { name: "Save goal" }));
  await screen.findByDisplayValue(goal.title);
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
});
