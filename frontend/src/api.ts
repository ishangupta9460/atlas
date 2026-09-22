export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function request<T>(path: string, token: string | null, options: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      headers: { ...options.headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") throw error;
    throw new Error("Atlas couldn't connect. Check your connection and try again.");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(response.status, body?.message ?? "Something went wrong. Please try again.");
  }
  return response.json() as Promise<T>;
}

export function jsonBody(value: unknown): RequestInit {
  return { headers: { "Content-Type": "application/json" }, body: JSON.stringify(value) };
}

export type Client = <T>(path: string, options?: RequestInit) => Promise<T>;
export const messageOf = (error: unknown) => error instanceof Error ? error.message : "Please try again.";
