export interface Endpoints {
  coreUrl: string;
  aiUrl: string;
}

export interface HealthState {
  core: "online" | "down" | "checking";
  ai: "online" | "down" | "checking";
}

export interface AssistantAction {
  action: string;
  approved: boolean;
  reason: string;
  confidence: number;
  app?: string | null;
  query?: string | null;
}

export interface AssistantChatResponse {
  response: string;
  model: string;
  provider: string;
  contextSummary: string;
  suggestedActions: AssistantAction[];
}

export interface SearchResult {
  path: string;
  name: string;
  extension: string;
  sizeBytes: number;
  modifiedAt: string;
  score: number;
  matchType: string;
  snippet?: string | null;
  reason: string;
}

export interface SearchResponse {
  query: string;
  indexedCount: number;
  roots: string[];
  results: SearchResult[];
}

export interface GuardianResponse {
  eventCount: number;
  anomalyDetected: boolean;
  reason: string;
  severity: string;
  score: number;
  signals: string[];
}

export interface BehaviorResponse {
  eventCount: number;
  prediction: {
    predictedAction: string;
    confidence: number;
    reason: string;
  };
  enqueue: {
    enqueued: boolean;
    reason: string;
    commandId?: number | null;
  };
}

export interface WorkflowStepResponse {
  id: number | null;
  workflowRunId: number;
  stepOrder: number;
  action: string;
  params: JsonRecord;
  priority: number;
  maxAttempts: number;
  dependsOn: number[];
  rollbackAction: string | null;
  rollbackParams: JsonRecord | null;
  status: "QUEUED" | "IN_PROGRESS" | "SUCCEEDED" | "FAILED" | "SKIPPED";
  commandId: number | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowRunResponse {
  id: number | null;
  name: string | null;
  source: string | null;
  status: "QUEUED" | "IN_PROGRESS" | "SUCCEEDED" | "FAILED" | "SKIPPED";
  totalSteps: number;
  completedSteps: number;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  steps: WorkflowStepResponse[];
}

export interface WorkflowStepRequest {
  action: string;
  app?: string;
  params?: Record<string, unknown>;
  priority?: number;
  maxAttempts?: number;
  dependsOn?: number[];
  rollbackAction?: string;
  rollbackParams?: Record<string, unknown>;
}

export interface WorkflowConditionRequest {
  leftKey: string;
  operator: "LT" | "LTE" | "GT" | "GTE" | "EQ" | "NEQ";
  rightValue: unknown;
}

export interface WorkflowCreateRequest {
  name?: string;
  source?: string;
  condition?: WorkflowConditionRequest;
  context?: Record<string, unknown>;
  steps: WorkflowStepRequest[];
}

export type JsonRecord = Record<string, unknown>;

export async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });
  const body = (await response.json().catch(() => ({}))) as { detail?: string };
  if (!response.ok) {
    throw new Error(typeof body.detail === "string" ? body.detail : `HTTP ${response.status}`);
  }
  return body as T;
}

export function joinUrl(baseUrl: string, path: string): string {
  return `${baseUrl.trim().replace(/\/$/, "")}${path}`;
}

export async function checkHealth(endpoints: Endpoints): Promise<HealthState> {
  const [core, ai] = await Promise.allSettled([
    fetchJson<JsonRecord>(joinUrl(endpoints.coreUrl, "/health")),
    fetchJson<JsonRecord>(joinUrl(endpoints.aiUrl, "/health")),
  ]);
  return {
    core: core.status === "fulfilled" ? "online" : "down",
    ai: ai.status === "fulfilled" ? "online" : "down",
  };
}

