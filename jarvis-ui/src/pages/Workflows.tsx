import {
  Play,
  Plus,
  Clock,
  AlertTriangle,
  CheckCircle2,
  RefreshCw,
  X,
  ChevronRight,
  Layers,
  Zap,
  XCircle,
  SkipForward,
  Loader2,
} from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import type { JarvisApi } from "../hooks/useJarvisApi";
import type {
  WorkflowRunResponse,
  WorkflowStepResponse,
  WorkflowCreateRequest,
  WorkflowStepRequest,
} from "../services/jarvisApi";

interface WorkflowsProps {
  api: JarvisApi;
}

type ViewMode = "library" | "detail" | "create";

const STATUS_CONFIG: Record<string, { icon: typeof CheckCircle2; className: string; label: string }> = {
  SUCCEEDED: { icon: CheckCircle2, className: "status-succeeded", label: "Completed" },
  FAILED: { icon: XCircle, className: "status-failed", label: "Failed" },
  IN_PROGRESS: { icon: Loader2, className: "status-running", label: "Running" },
  QUEUED: { icon: Clock, className: "status-queued", label: "Queued" },
  SKIPPED: { icon: SkipForward, className: "status-skipped", label: "Skipped" },
};

function formatTime(isoString: string): string {
  try {
    const date = new Date(isoString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return "Just now";
    if (diffMins < 60) return `${diffMins}m ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}h ago`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
  } catch {
    return isoString;
  }
}

export function Workflows({ api }: WorkflowsProps) {
  const [workflows, setWorkflows] = useState<WorkflowRunResponse[]>([]);
  const [selectedWorkflow, setSelectedWorkflow] = useState<WorkflowRunResponse | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>("library");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadWorkflows = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const result = await api.fetchWorkflows(25);
      setWorkflows(result);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to load workflows";
      setError(msg);
      api.markError(msg);
    } finally {
      setIsLoading(false);
    }
  }, [api]);

  useEffect(() => {
    loadWorkflows();
  }, [loadWorkflows]);

  function handleSelectWorkflow(wf: WorkflowRunResponse) {
    setSelectedWorkflow(wf);
    setViewMode("detail");
  }

  function handleBackToLibrary() {
    setSelectedWorkflow(null);
    setViewMode("library");
  }

  async function handleCreateWorkflow(request: WorkflowCreateRequest) {
    try {
      const created = await api.createWorkflow(request);
      setWorkflows((prev) => [created, ...prev]);
      setSelectedWorkflow(created);
      setViewMode("detail");
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Failed to create workflow";
      setError(msg);
      api.markError(msg);
    }
  }

  const succeeded = workflows.filter((w) => w.status === "SUCCEEDED").length;
  const failed = workflows.filter((w) => w.status === "FAILED").length;
  const running = workflows.filter((w) => w.status === "IN_PROGRESS" || w.status === "QUEUED").length;

  return (
    <div className="page-content workflows-layout">
      <div className="workflows-sidebar">
        <button
          className="primary-action-btn wf-create-btn"
          onClick={() => setViewMode("create")}
          type="button"
        >
          <Plus size={18} />
          CREATE NEW WORKFLOW
        </button>

        <div className="wf-stats-row">
          <div className="wf-stat">
            <span className="wf-stat-value text-mint">{succeeded}</span>
            <span className="wf-stat-label">Completed</span>
          </div>
          <div className="wf-stat">
            <span className="wf-stat-value text-rose">{failed}</span>
            <span className="wf-stat-label">Failed</span>
          </div>
          <div className="wf-stat">
            <span className="wf-stat-value text-cyan">{running}</span>
            <span className="wf-stat-label">Active</span>
          </div>
        </div>

        <div className="panel workflow-library">
          <div className="panel-heading">
            <h2>Library</h2>
            <button
              className="icon-btn"
              disabled={isLoading}
              onClick={loadWorkflows}
              type="button"
              aria-label="Refresh workflows"
            >
              <RefreshCw size={16} className={isLoading ? "spin-icon" : ""} />
            </button>
          </div>

          {error && (
            <div className="wf-error-notice">
              <AlertTriangle size={14} />
              <span>{error}</span>
            </div>
          )}

          <div className="library-list">
            {workflows.length === 0 && !isLoading && (
              <div className="wf-empty-state">
                <Layers size={32} />
                <p>No workflows yet</p>
                <small>Create your first workflow to get started</small>
              </div>
            )}

            {isLoading && workflows.length === 0 && (
              <div className="wf-empty-state">
                <Loader2 size={32} className="spin-icon" />
                <p>Loading workflows...</p>
              </div>
            )}

            {workflows.map((wf) => {
              const statusCfg = STATUS_CONFIG[wf.status] ?? STATUS_CONFIG.QUEUED;
              const StatusIcon = statusCfg.icon;
              const isSelected = selectedWorkflow?.id === wf.id;
              return (
                <button
                  key={wf.id ?? wf.createdAt}
                  className={`library-item ${isSelected ? "selected" : ""}`}
                  onClick={() => handleSelectWorkflow(wf)}
                  type="button"
                >
                  <StatusIcon
                    size={16}
                    className={`${statusCfg.className} ${wf.status === "IN_PROGRESS" ? "spin-icon" : ""}`}
                  />
                  <div className="library-item-info">
                    <span className="library-item-name">
                      {wf.name || `Workflow #${wf.id ?? "?"}`}
                    </span>
                    <small>
                      {wf.completedSteps}/{wf.totalSteps} steps · {formatTime(wf.createdAt)}
                    </small>
                  </div>
                  <ChevronRight size={14} className="library-chevron" />
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="panel large-panel canvas-panel">
        {viewMode === "library" && <LibraryOverview workflows={workflows} onSelect={handleSelectWorkflow} />}
        {viewMode === "detail" && selectedWorkflow && (
          <WorkflowDetail workflow={selectedWorkflow} onBack={handleBackToLibrary} />
        )}
        {viewMode === "create" && (
          <WorkflowCreator onSubmit={handleCreateWorkflow} onCancel={handleBackToLibrary} />
        )}
      </div>
    </div>
  );
}

/* ---- Library Overview (default canvas view) ---- */
interface LibraryOverviewProps {
  workflows: WorkflowRunResponse[];
  onSelect: (wf: WorkflowRunResponse) => void;
}

function LibraryOverview({ workflows, onSelect }: LibraryOverviewProps) {
  const recent = workflows.slice(0, 6);
  return (
    <>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Studio Workspace</p>
          <h2>Workflow Overview</h2>
        </div>
      </div>

      {recent.length === 0 ? (
        <div className="wf-canvas-empty">
          <Layers size={48} />
          <h3>No Workflows Yet</h3>
          <p>Click "Create New Workflow" to build your first automation pipeline with DAG-based step dependencies.</p>
        </div>
      ) : (
        <div className="wf-overview-grid">
          {recent.map((wf) => {
            const statusCfg = STATUS_CONFIG[wf.status] ?? STATUS_CONFIG.QUEUED;
            const StatusIcon = statusCfg.icon;
            return (
              <button
                key={wf.id ?? wf.createdAt}
                className="wf-overview-card"
                onClick={() => onSelect(wf)}
                type="button"
              >
                <div className="wf-card-header">
                  <StatusIcon size={18} className={statusCfg.className} />
                  <span className={`wf-card-badge ${statusCfg.className}`}>{statusCfg.label}</span>
                </div>
                <h3>{wf.name || `Workflow #${wf.id}`}</h3>
                <div className="wf-card-meta">
                  <span>{wf.totalSteps} steps</span>
                  <span>{wf.completedSteps} done</span>
                </div>
                <div className="wf-card-progress">
                  <div
                    className="wf-card-progress-fill"
                    style={{
                      width: `${wf.totalSteps > 0 ? (wf.completedSteps / wf.totalSteps) * 100 : 0}%`,
                    }}
                  />
                </div>
                <small className="wf-card-time">{formatTime(wf.createdAt)}</small>
              </button>
            );
          })}
        </div>
      )}
    </>
  );
}

/* ---- Workflow Detail View ---- */
interface WorkflowDetailProps {
  workflow: WorkflowRunResponse;
  onBack: () => void;
}

function WorkflowDetail({ workflow, onBack }: WorkflowDetailProps) {
  const statusCfg = STATUS_CONFIG[workflow.status] ?? STATUS_CONFIG.QUEUED;
  const StatusIcon = statusCfg.icon;

  return (
    <>
      <div className="panel-heading">
        <div>
          <button className="wf-back-btn" onClick={onBack} type="button">
            ← Back to Library
          </button>
          <h2>{workflow.name || `Workflow #${workflow.id}`}</h2>
        </div>
        <div className={`wf-status-pill ${statusCfg.className}`}>
          <StatusIcon size={14} />
          {statusCfg.label}
        </div>
      </div>

      {/* Summary row */}
      <div className="wf-detail-summary">
        <div className="wf-detail-stat">
          <span>Total Steps</span>
          <strong>{workflow.totalSteps}</strong>
        </div>
        <div className="wf-detail-stat">
          <span>Completed</span>
          <strong>{workflow.completedSteps}</strong>
        </div>
        <div className="wf-detail-stat">
          <span>Source</span>
          <strong>{workflow.source ?? "—"}</strong>
        </div>
        <div className="wf-detail-stat">
          <span>Created</span>
          <strong>{formatTime(workflow.createdAt)}</strong>
        </div>
      </div>

      {workflow.lastError && (
        <div className="wf-error-notice">
          <AlertTriangle size={14} />
          <span>{workflow.lastError}</span>
        </div>
      )}

      {/* DAG Visualization */}
      <div className="wf-dag-section">
        <h3 className="wf-section-title">Pipeline Steps</h3>
        <div className="wf-dag-canvas">
          {workflow.steps.length === 0 ? (
            <div className="wf-empty-state">
              <p>No steps in this workflow</p>
            </div>
          ) : (
            <div className="wf-step-flow">
              {workflow.steps.map((step, idx) => (
                <StepNode key={step.id ?? idx} step={step} isLast={idx === workflow.steps.length - 1} />
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}

interface StepNodeProps {
  step: WorkflowStepResponse;
  isLast: boolean;
}

function StepNode({ step, isLast }: StepNodeProps) {
  const statusCfg = STATUS_CONFIG[step.status] ?? STATUS_CONFIG.QUEUED;
  const StatusIcon = statusCfg.icon;

  return (
    <div className="wf-step-wrapper">
      <div className={`wf-step-node ${statusCfg.className}`}>
        <div className="wf-step-header">
          <StatusIcon
            size={16}
            className={step.status === "IN_PROGRESS" ? "spin-icon" : ""}
          />
          <span className="wf-step-order">Step {step.stepOrder}</span>
          <span className={`wf-step-badge ${statusCfg.className}`}>{statusCfg.label}</span>
        </div>
        <div className="wf-step-action">
          <Zap size={14} />
          <strong>{step.action}</strong>
        </div>
        <div className="wf-step-meta">
          <small>Priority: {step.priority}</small>
          <small>Attempts: {step.maxAttempts}</small>
          {step.dependsOn.length > 0 && (
            <small>Depends on: {step.dependsOn.join(", ")}</small>
          )}
        </div>
        {step.rollbackAction && (
          <div className="wf-step-rollback">
            <small>Rollback: {step.rollbackAction}</small>
          </div>
        )}
        {step.lastError && (
          <div className="wf-step-error">
            <AlertTriangle size={12} />
            <small>{step.lastError}</small>
          </div>
        )}
      </div>
      {!isLast && (
        <div className="wf-step-connector">
          <div className="wf-connector-line" />
          <ChevronRight size={14} className="wf-connector-arrow" />
        </div>
      )}
    </div>
  );
}

/* ---- Workflow Creator ---- */
interface WorkflowCreatorProps {
  onSubmit: (request: WorkflowCreateRequest) => Promise<void>;
  onCancel: () => void;
}

function WorkflowCreator({ onSubmit, onCancel }: WorkflowCreatorProps) {
  const [name, setName] = useState("");
  const [steps, setSteps] = useState<WorkflowStepRequest[]>([
    { action: "", priority: 5, maxAttempts: 3 },
  ]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  function addStep() {
    setSteps((prev) => [...prev, { action: "", priority: 5, maxAttempts: 3 }]);
  }

  function removeStep(index: number) {
    if (steps.length <= 1) return;
    setSteps((prev) => prev.filter((_, i) => i !== index));
  }

  function updateStep(index: number, updates: Partial<WorkflowStepRequest>) {
    setSteps((prev) =>
      prev.map((step, i) => (i === index ? { ...step, ...updates } : step)),
    );
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const validSteps = steps.filter((s) => s.action.trim());
    if (validSteps.length === 0) {
      setSubmitError("At least one step with an action is required");
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await onSubmit({
        name: name.trim() || undefined,
        steps: validSteps,
      });
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : "Failed to create workflow");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">New Automation</p>
          <h2>Create Workflow</h2>
        </div>
        <button className="icon-btn" onClick={onCancel} type="button" aria-label="Cancel">
          <X size={20} />
        </button>
      </div>

      <form className="wf-create-form" onSubmit={handleSubmit}>
        <label className="wf-form-field">
          <span>Workflow Name</span>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Morning Routine, System Backup..."
          />
        </label>

        <div className="wf-steps-section">
          <div className="wf-steps-header">
            <h3>Pipeline Steps</h3>
            <button className="secondary-btn" onClick={addStep} type="button">
              <Plus size={14} />
              Add Step
            </button>
          </div>

          {steps.map((step, idx) => (
            <div className="wf-step-editor" key={idx}>
              <div className="wf-step-editor-header">
                <span className="wf-step-number">Step {idx + 1}</span>
                {steps.length > 1 && (
                  <button
                    className="icon-btn danger"
                    onClick={() => removeStep(idx)}
                    type="button"
                    aria-label="Remove step"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>
              <div className="wf-step-editor-fields">
                <label>
                  <span>Action</span>
                  <input
                    type="text"
                    value={step.action}
                    onChange={(e) => updateStep(idx, { action: e.target.value })}
                    placeholder="e.g. OPEN_APP, SET_STATUS, RUN_SCRIPT..."
                    required
                  />
                </label>
                <label>
                  <span>App (optional)</span>
                  <input
                    type="text"
                    value={step.app ?? ""}
                    onChange={(e) => updateStep(idx, { app: e.target.value || undefined })}
                    placeholder="e.g. Slack, Spotify..."
                  />
                </label>
                <div className="wf-step-editor-row">
                  <label>
                    <span>Priority</span>
                    <input
                      type="number"
                      min={1}
                      max={10}
                      value={step.priority ?? 5}
                      onChange={(e) => updateStep(idx, { priority: Number(e.target.value) })}
                    />
                  </label>
                  <label>
                    <span>Max Attempts</span>
                    <input
                      type="number"
                      min={1}
                      max={10}
                      value={step.maxAttempts ?? 3}
                      onChange={(e) => updateStep(idx, { maxAttempts: Number(e.target.value) })}
                    />
                  </label>
                  <label>
                    <span>Depends On</span>
                    <input
                      type="text"
                      value={step.dependsOn?.join(", ") ?? ""}
                      onChange={(e) => {
                        const deps = e.target.value
                          .split(",")
                          .map((s) => parseInt(s.trim(), 10))
                          .filter((n) => !isNaN(n));
                        updateStep(idx, { dependsOn: deps.length > 0 ? deps : undefined });
                      }}
                      placeholder="e.g. 1, 2"
                    />
                  </label>
                </div>
                <label>
                  <span>Rollback Action (optional)</span>
                  <input
                    type="text"
                    value={step.rollbackAction ?? ""}
                    onChange={(e) =>
                      updateStep(idx, { rollbackAction: e.target.value || undefined })
                    }
                    placeholder="e.g. REVERT_STATUS..."
                  />
                </label>
              </div>
            </div>
          ))}
        </div>

        {submitError && (
          <div className="wf-error-notice">
            <AlertTriangle size={14} />
            <span>{submitError}</span>
          </div>
        )}

        <div className="wf-form-actions">
          <button className="secondary-btn" onClick={onCancel} type="button">
            Cancel
          </button>
          <button className="primary-action-btn" disabled={isSubmitting} type="submit">
            {isSubmitting ? (
              <>
                <Loader2 size={16} className="spin-icon" />
                Creating...
              </>
            ) : (
              <>
                <Play size={16} />
                Create & Run Workflow
              </>
            )}
          </button>
        </div>
      </form>
    </>
  );
}
