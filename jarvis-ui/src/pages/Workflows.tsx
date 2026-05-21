import { Play, Plus, Clock, AlertTriangle, CheckCircle2 } from "lucide-react";
import type { JarvisApi } from "../hooks/useJarvisApi";

interface WorkflowsProps {
  api: JarvisApi;
}

export function Workflows({ api }: WorkflowsProps) {
  return (
    <div className="page-content workflows-layout">
      <div className="workflows-sidebar">
        <button className="primary-action-btn">
          <Plus size={18} />
          CREATE NEW WORKFLOW
        </button>
        
        <div className="panel workflow-library">
          <div className="panel-heading">
            <h2>Library</h2>
          </div>
          <div className="library-list">
            <div className="library-item">
              <Play size={14} className="icon-play" />
              <span>Morning Routine</span>
            </div>
            <div className="library-item">
              <Play size={14} className="icon-play" />
              <span>System Backup</span>
            </div>
            <div className="library-item">
              <Play size={14} className="icon-play" />
              <span>Deep Work Mode</span>
            </div>
          </div>
        </div>

        <div className="panel execution-history">
          <div className="panel-heading">
            <h2>Execution History</h2>
          </div>
          <div className="history-list">
            <div className="history-item success">
              <CheckCircle2 size={14} />
              <span>Morning Routine</span>
              <small>08:00 AM</small>
            </div>
            <div className="history-item success">
              <CheckCircle2 size={14} />
              <span>System Backup</span>
              <small>03:00 AM</small>
            </div>
            <div className="history-item error">
              <AlertTriangle size={14} />
              <span>Deep Work Mode</span>
              <small>Yesterday</small>
            </div>
          </div>
        </div>
      </div>

      <div className="panel large-panel canvas-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Studio Workspace</p>
            <h2>Workflow Builder Canvas</h2>
          </div>
        </div>
        <div className="canvas-placeholder">
          {/* Node-based builder canvas visualization */}
          <div className="canvas-grid">
            <div className="workflow-node trigger-node" style={{ top: "20%", left: "10%" }}>
              <Clock size={16} />
              Schedule 08:00
            </div>
            <svg className="node-connector" style={{ top: "25%", left: "30%", width: "100px", height: "20px" }}>
              <line x1="0" y1="10" x2="100" y2="10" stroke="var(--line-strong)" strokeWidth="2" strokeDasharray="4" />
            </svg>
            <div className="workflow-node action-node" style={{ top: "20%", left: "45%" }}>
              API: Slack Status
            </div>
            <svg className="node-connector" style={{ top: "25%", left: "65%", width: "100px", height: "20px" }}>
              <line x1="0" y1="10" x2="100" y2="10" stroke="var(--line-strong)" strokeWidth="2" strokeDasharray="4" />
            </svg>
            <div className="workflow-node action-node" style={{ top: "20%", left: "80%" }}>
              API: Spotify Focus
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
