import { useCallback, useState } from "react";
import {
  type AssistantChatResponse,
  type BehaviorResponse,
  checkHealth,
  type Endpoints,
  fetchJson,
  type GuardianResponse,
  type HealthState,
  type JsonRecord,
  joinUrl,
  type SearchResponse,
} from "../services/jarvisApi";

export type ConsoleMode = "idle" | "thinking" | "guardian" | "success" | "error";

export function useJarvisApi() {
  const [endpoints, setEndpoints] = useState<Endpoints>({
    coreUrl: "http://127.0.0.1:8080",
    aiUrl: "http://127.0.0.1:8000",
  });
  const [health, setHealth] = useState<HealthState>({ core: "checking", ai: "checking" });
  const [mode, setMode] = useState<ConsoleMode>("idle");
  const [status, setStatus] = useState("Ready");

  const refreshHealth = useCallback(async () => {
    setMode("thinking");
    setStatus("Checking services");
    const nextHealth = await checkHealth(endpoints);
    setHealth(nextHealth);
    const allOnline = nextHealth.core === "online" && nextHealth.ai === "online";
    setMode(allOnline ? "success" : "error");
    setStatus(allOnline ? "Core and AI online" : "One or more services are down");
    return nextHealth;
  }, [endpoints]);

  const postAssistantChat = useCallback(
    async (message: string, provider: string): Promise<AssistantChatResponse> => {
      setMode("thinking");
      setStatus("Assistant is reasoning");
      const response = await fetchJson<AssistantChatResponse>(joinUrl(endpoints.coreUrl, "/assistant/chat"), {
        method: "POST",
        body: JSON.stringify({ message, provider, eventLimit: 20 }),
      });
      setMode("success");
      setStatus("Assistant response ready");
      return response;
    },
    [endpoints.coreUrl],
  );

  const runSearch = useCallback(
    async (query: string, roots: string[]): Promise<SearchResponse> => {
      setMode("thinking");
      setStatus("Searching local files");
      const response = await fetchJson<SearchResponse>(joinUrl(endpoints.coreUrl, "/search"), {
        method: "POST",
        body: JSON.stringify({ query, roots, maxResults: 8, includeContent: true }),
      });
      setMode("success");
      setStatus(`Indexed ${response.indexedCount} files`);
      return response;
    },
    [endpoints.coreUrl],
  );

  const runGuardianScan = useCallback(async (): Promise<GuardianResponse> => {
    setMode("guardian");
    setStatus("Guardian scan running");
    const response = await fetchJson<GuardianResponse>(joinUrl(endpoints.coreUrl, "/guardian/anomaly"), {
      method: "POST",
      body: JSON.stringify({ eventLimit: 120 }),
    });
    setMode(response.anomalyDetected ? "guardian" : "success");
    setStatus("Guardian scan complete");
    return response;
  }, [endpoints.coreUrl]);

  const runBehaviorPrediction = useCallback(async (): Promise<BehaviorResponse> => {
    setMode("thinking");
    setStatus("Predicting behavior");
    const response = await fetchJson<BehaviorResponse>(joinUrl(endpoints.coreUrl, "/behavior-learning/predict"), {
      method: "POST",
      body: JSON.stringify({ eventLimit: 80, enqueueIfSafe: false }),
    });
    setMode("success");
    setStatus("Behavior prediction ready");
    return response;
  }, [endpoints.coreUrl]);

  const fetchCoreList = useCallback(
    async <T,>(path: string): Promise<T> => fetchJson<T>(joinUrl(endpoints.coreUrl, path)),
    [endpoints.coreUrl],
  );

  const fetchInsights = useCallback(
    async (): Promise<JsonRecord> => fetchJson<JsonRecord>(joinUrl(endpoints.coreUrl, "/insights/daily")),
    [endpoints.coreUrl],
  );

  const markError = useCallback((message: string) => {
    setMode("error");
    setStatus(message);
  }, []);

  return {
    endpoints,
    setEndpoints,
    health,
    mode,
    status,
    setStatus,
    refreshHealth,
    postAssistantChat,
    runSearch,
    runGuardianScan,
    runBehaviorPrediction,
    fetchCoreList,
    fetchInsights,
    markError,
  };
}
