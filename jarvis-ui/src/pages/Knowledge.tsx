import { SearchPanel } from "../components/SearchPanel";
import type { JarvisApi } from "../hooks/useJarvisApi";

interface KnowledgeProps {
  api: JarvisApi;
}

export function Knowledge({ api }: KnowledgeProps) {
  return (
    <div className="page-content knowledge-layout">
      <div className="panel large-panel graph-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Neural Network</p>
            <h2>Knowledge Graph</h2>
          </div>
        </div>
        <div className="graph-placeholder">
          {/* Neural network visualization goes here */}
          <svg width="100%" height="100%" viewBox="0 0 400 300" className="neural-svg">
            <line x1="200" y1="150" x2="100" y2="80" stroke="var(--line-strong)" strokeWidth="2"/>
            <line x1="200" y1="150" x2="300" y2="80" stroke="var(--line-strong)" strokeWidth="2"/>
            <line x1="200" y1="150" x2="100" y2="220" stroke="var(--line-strong)" strokeWidth="2"/>
            <line x1="200" y1="150" x2="300" y2="220" stroke="var(--line-strong)" strokeWidth="2"/>
            
            <circle cx="200" cy="150" r="12" fill="var(--cyan)" />
            <circle cx="100" cy="80" r="8" fill="var(--blue)" />
            <circle cx="300" cy="80" r="8" fill="var(--mint)" />
            <circle cx="100" cy="220" r="8" fill="var(--amber)" />
            <circle cx="300" cy="220" r="8" fill="var(--rose)" />
          </svg>
        </div>
      </div>

      <div className="knowledge-sidebar">
        <SearchPanel onError={api.markError} onSearch={api.runSearch} />
        
        <div className="panel">
          <div className="panel-heading">
            <h2>Knowledge Sources</h2>
          </div>
          <div className="sources-list">
            <div className="source-item online">
              <div className="source-dot"></div>
              <span>Core API</span>
              <small>Syncing</small>
            </div>
            <div className="source-item online">
              <div className="source-dot"></div>
              <span>VS Code</span>
              <small>Active</small>
            </div>
            <div className="source-item offline">
              <div className="source-dot"></div>
              <span>Slack</span>
              <small>Disconnected</small>
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-heading">
            <h2>Learned Concepts</h2>
          </div>
          <div className="concepts-list">
            <div className="concept-chip">Focus Time: Morning</div>
            <div className="concept-chip">Project: JARVIS</div>
            <div className="concept-chip">Language: TypeScript</div>
          </div>
        </div>
      </div>
    </div>
  );
}
