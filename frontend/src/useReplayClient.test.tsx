import { renderHook } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import useReplayClient from "./useReplayClient";
import { Client, jsonBody } from "./api";

it("reuses an uncertain mutation's key and gives a new action a fresh key", async () => {
  const client = vi.fn().mockRejectedValueOnce(new Error("Response lost")).mockResolvedValue({ ok: true });
  const { result } = renderHook(() => useReplayClient(client as Client));
  const options = { method: "POST", ...jsonBody({ reaction: "liked" }) };
  await expect(result.current("/resources/1/feedback", options)).rejects.toThrow("Response lost");
  await result.current("/resources/1/feedback", options);await result.current("/resources/1/feedback", options);
  const key = (i: number) => client.mock.calls[i][1].headers["Idempotency-Key"];
  expect(key(0)).toBe(key(1));expect(key(2)).not.toBe(key(1));
});
