import { Save, RotateCcw } from "lucide-react";
import type { JarvisApi } from "../hooks/useJarvisApi";

interface SettingsProps {
  api: JarvisApi;
}

export function Settings({ api }: SettingsProps) {
  return (
    <div className="page-content settings-layout">
      <div className="settings-header">
        <div>
          <p className="eyebrow">Configuration</p>
          <h2>Application Settings</h2>
        </div>
        <div className="settings-actions">
          <button className="secondary-btn">
            <RotateCcw size={16} />
            Restore Defaults
          </button>
          <button className="primary-action-btn">
            <Save size={16} />
            SAVE CHANGES
          </button>
        </div>
      </div>

      <div className="settings-grid">
        <div className="panel">
          <div className="panel-heading">
            <h2>Core & AI Connection</h2>
          </div>
          <div className="settings-form">
            <label>
              <span>Core URL</span>
              <input type="url" value={api.endpoints.coreUrl} readOnly />
            </label>
            <label>
              <span>AI URL</span>
              <input type="url" value={api.endpoints.aiUrl} readOnly />
            </label>
            <p className="status-text">Data stream active. Local processing mode.</p>
          </div>
        </div>

        <div className="panel">
          <div className="panel-heading">
            <h2>Data & Privacy</h2>
          </div>
          <div className="settings-list">
            <div className="setting-item">
              <div className="setting-info">
                <span>Local Processing Mode</span>
                <small>Keep all data on-device</small>
              </div>
              <div className="toggle active"></div>
            </div>
            <button className="secondary-btn">Export Data</button>
            <button className="danger-btn">Delete All Data</button>
          </div>
        </div>

        <div className="panel">
          <div className="panel-heading">
            <h2>Account & Integrations</h2>
          </div>
          <div className="settings-list">
            <div className="integration-item">
              <span>Slack</span>
              <button className="secondary-btn">Connect</button>
            </div>
            <div className="integration-item">
              <span>VS Code</span>
              <button className="secondary-btn active">Disconnect</button>
            </div>
            <div className="integration-item">
              <span>Google Calendar</span>
              <button className="secondary-btn">Connect</button>
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-heading">
            <h2>Advanced</h2>
          </div>
          <div className="settings-form">
            <label>
              <span>LLM Provider</span>
              <select>
                <option>Local (Ollama)</option>
                <option>Cloud (OpenAI)</option>
                <option>Cloud (Anthropic)</option>
              </select>
            </label>
            <label>
              <span>API Access Keys</span>
              <input type="password" value="****************" readOnly />
            </label>
          </div>
        </div>
      </div>
    </div>
  );
}
