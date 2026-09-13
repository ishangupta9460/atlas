import { useEffect, useState } from "react";
import TodayScreen from "./TodayScreen";

/**
 * The health check remains from the frontend scaffold. FOUND-003 adds the
 * deliberately bare task loop below; the full Today UI remains a later story.
 */
function App() {
  const [status, setStatus] = useState<"checking" | "ok" | "unreachable">("checking");

  useEffect(() => {
    fetch("/api/health")
      .then((res) => (res.ok ? setStatus("ok") : setStatus("unreachable")))
      .catch(() => setStatus("unreachable"));
  }, []);

  return (
    <div className="min-h-screen bg-neutral-50 p-6">
      <div className="space-y-4">
        <h1 className="text-2xl font-semibold text-neutral-800">Atlas</h1>
        <p className="text-neutral-500">Environment setup — backend status: {status}</p>
        <TodayScreen />
      </div>
    </div>
  );
}

export default App;
