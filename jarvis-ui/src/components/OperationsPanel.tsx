import { RefreshCw } from "lucide-react";
import { useState } from "react";
import type { JsonRecord } from "../services/jarvisApi";

type OpsView = "insights" | "commands" | "workflows" | "events";

interface OperationsPanelProps {
  fetchInsights: () => Promise<JsonRecord>;
  fetchCoreList: <T>(path: string) => Promise<T>;
  onError: (message: string) => void;
}

const OPS_TABS: Array<{ id: OpsView; label: string }> = [
  { id: "insights", label: "Insights" },
  { id: "commands", label: "Commands" },
  { id: "workflows", label: "Workflows" },
  { id: "events", label: "Events" },
];

export function OperationsPanel({ fetchInsights, fetchCoreList, onError }: OperationsPanelProps) {
  const [view, setView] = useState<OpsView>("insights");
  const [items, setItems] = useState<JsonRecord[]>([]);
  const [summary, setSummary] = useState<JsonRecord | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  async function refresh(nextView = view) {
    setIsRefreshing(true);
    try {
      if (nextView === "insights") {
        setSummary(await fetchInsights());
        setItems([]);
      } else {
        const path = nextView === "commands" ? "/command" : nextView === "workflows" ? "/workflow" : "/event?limit=12";
        const response = await fetchCoreList<JsonRecord[]>(path);
        setItems(Array.isArray(response) ? response : []);
        setSummary(null);
      }
    } catch (error) {
      onError(error instanceof Error ? error.message : "Operations refresh failed");
    } finally {
      setIsRefreshing(false);
    }
  }

  function selectView(nextView: OpsView) {
    setView(nextView);
    refresh(nextView);
  }

  return (
    <section className="panel ops-panel" aria-labelledby="ops-title">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Runtime</p>
          <h2 id="ops-title">Operations</h2>
        </div>
        <button disabled={isRefreshing} onClick={() => refresh()} type="button">
          <RefreshCw size={16} />
          Refresh
        </button>
      </div>

      <div className="tabs" role="tablist" aria-label="Operations views">
        {OPS_TABS.map((tab) => (
          <button
            aria-selected={view === tab.id}
            className={`tab ${view === tab.id ? "active" : ""}`}
            key={tab.id}
            onClick={() => selectView(tab.id)}
            role="tab"
            type="button"
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="result-list">
        {summary ? (
          <SummaryRows summary={summary} />
        ) : items.length ? (
          items.slice(0, 12).map((item, index) => <OpsRow item={item} key={`${view}-${String(item.id ?? index)}`} view={view} />)
        ) : (
          <article className="notice">No records loaded.</article>
        )}
      </div>
    </section>
  );
}

interface SummaryRowsProps {
  summary: JsonRecord;
}

function SummaryRows({ summary }: SummaryRowsProps) {
  const rows = [
    ["Summary", valueFor(summary, "summary")],
    ["Date", valueFor(summary, "date")],
    ["Focus", valueFor(summary, "focusScore") || valueFor(summary, "focus_score")],
  ];
  return (
    <>
      {rows.map(([label, value]) => (
        <article className="data-row" key={label}>
          <span>{label}</span>
          <strong>{value || "n/a"}</strong>
        </article>
      ))}
    </>
  );
}

interface OpsRowProps {
  item: JsonRecord;
  view: OpsView;
}

function OpsRow({ item, view }: OpsRowProps) {
  const label =
    view === "commands"
      ? `${valueFor(item, "action")} · ${valueFor(item, "status")}`
      : view === "workflows"
        ? `${valueFor(item, "name") || "workflow"} · ${valueFor(item, "status")}`
        : `${valueFor(item, "type")} · ${valueFor(item, "source") || "unknown"}`;
  const timestamp = valueFor(item, "createdAt") || valueFor(item, "updatedAt") || valueFor(item, "scheduledAt");

  return (
    <article className="data-row">
      <span>{label}</span>
      <strong>{timestamp}</strong>
    </article>
  );
}

function valueFor(item: JsonRecord, key: string): string {
  const value = item[key];
  if (value == null) {
    return "";
  }
  return String(value);
}
