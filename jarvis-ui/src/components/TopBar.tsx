import { Activity, Radar } from "lucide-react";
import type { FormEvent } from "react";
import type { Endpoints, HealthState } from "../services/jarvisApi";

interface TopBarProps {
  endpoints: Endpoints;
  health: HealthState;
  onEndpointsChange: (endpoints: Endpoints) => void;
  onRefreshHealth: () => void;
}

export function TopBar({ endpoints, health, onEndpointsChange, onRefreshHealth }: TopBarProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onRefreshHealth();
  }

  return (
    <header className="topbar" aria-label="Service controls">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">
          <Radar size={24} />
        </span>
        <div>
          <p className="eyebrow">JARVIS</p>
          <h1>Command Console</h1>
        </div>
      </div>

      <form className="endpoint-form" onSubmit={handleSubmit}>
        <label>
          <span>Core</span>
          <input
            type="url"
            value={endpoints.coreUrl}
            onChange={(event) => onEndpointsChange({ ...endpoints, coreUrl: event.target.value })}
          />
        </label>
        <label>
          <span>AI</span>
          <input
            type="url"
            value={endpoints.aiUrl}
            onChange={(event) => onEndpointsChange({ ...endpoints, aiUrl: event.target.value })}
          />
        </label>
        <button type="submit">
          <Activity size={16} />
          Check
        </button>
      </form>

      <div className="service-pills" aria-label="Current service status">
        <span className={`pill ${health.core}`}>Core {health.core}</span>
        <span className={`pill ${health.ai}`}>AI {health.ai}</span>
      </div>
    </header>
  );
}
