import { useEffect, useState } from "react";

/**
 * This screen only exists to prove the frontend can reach the backend.
 * It is not part of the Atlas product UI (see 14_UI_UX_SPECIFICATION.md
 * for the real Today screen, built starting in a later phase).
 */
function App() {
  const [status, setStatus] = useState<"checking" | "ok" | "unreachable">("checking");

  useEffect(() => {
    fetch("/api/health")
      .then((res) => (res.ok ? setStatus("ok") : setStatus("unreachable")))
      .catch(() => setStatus("unreachable"));
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-neutral-50">
      <div className="text-center space-y-2">
        <h1 className="text-2xl font-semibold text-neutral-800">Atlas</h1>
        <p className="text-neutral-500">Environment setup — backend status: {status}</p>
      </div>
    </div>
  );
}

export default App;
