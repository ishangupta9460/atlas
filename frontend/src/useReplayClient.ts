import { useCallback, useRef } from "react";
import { ApiError, Client } from "./api";

/** Preserve a mutation key across uncertain network/server failures, as the execution UI does. */
export default function useReplayClient(client: Client): Client {
  const pending = useRef(new Map<string, string>());
  const files = useRef(new WeakMap<object, number>());
  const nextFile = useRef(0);
  return useCallback(async <T,>(path: string, options: RequestInit = {}): Promise<T> => {
    if (!options.method || options.method === "GET") return client<T>(path, options);
    const body = options.body instanceof FormData ? [...options.body.entries()].map(([name, value]) => {
      if (typeof value === "string") return [name, value];
      if (!files.current.has(value)) files.current.set(value, ++nextFile.current);
      return [name, value.name, value.size, files.current.get(value)];
    }) : options.body;
    const fingerprint = `${options.method}:${path}:${JSON.stringify(body)}`;
    let key = pending.current.get(fingerprint);
    if (!key) { key = crypto.randomUUID(); pending.current.set(fingerprint, key); }
    try {
      const result = await client<T>(path, { ...options, headers: { ...options.headers, "Idempotency-Key": key } });
      pending.current.delete(fingerprint); return result;
    } catch (e) { if (e instanceof ApiError && e.status < 500) pending.current.delete(fingerprint); throw e; }
  }, [client]);
}
