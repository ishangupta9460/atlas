import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import ResourcePanel from "./ResourcePanel";
import { Client } from "./api";
it("replaces only the attachment and records feedback without preferences", async () => {
  const user = userEvent.setup(); const first = { id: 1, title: "Guide", type: "link", urlOrFileRef: "https://example.com", addedBy: "user" }, second = { ...first, id: 2, title: "Book" };
  const call = vi.fn(async (path: string, options?: RequestInit) => {
    if (path === "/resources") return [first, second];
    if (path.endsWith("/feedback")) return { preferenceCreated: false };
    if (options?.method === "POST") return [second];return [first];
  });
  render(<ResourcePanel client={call as Client} taskId={8} />);
  await user.click(await screen.findByRole("button", { name: "Not helpful" }));
  expect(await screen.findByRole("status")).toHaveTextContent("No preference was created");
  await user.selectOptions(screen.getByLabelText("Replacement for Guide"), "2");await user.click(screen.getByRole("button", { name: "Replace" }));
  expect(await screen.findByRole("link", { name: "Book" })).toBeInTheDocument();
  expect(call.mock.calls.find(([path]) => path.endsWith("/replace"))?.[0]).toBe("/commitments/8/resources/1/replace");
  expect(call.mock.calls.some(([path]) => path === "/commitments")).toBe(false);
  expect(screen.getByText(/Pattern detection is disabled/)).toBeInTheDocument();
});
