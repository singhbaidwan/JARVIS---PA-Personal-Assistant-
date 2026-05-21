import { GuardianPanel } from "../components/GuardianPanel";
import type { JarvisApi } from "../hooks/useJarvisApi";

interface SystemHealthProps {
  api: JarvisApi;
}

export function SystemHealth({ api }: SystemHealthProps) {
  return (
    <div className="page-content health-layout">
      <div className="panel large-panel globe-panel">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">Diagnostics</p>
            <h2>Holographic Network Globe</h2>
          </div>
        </div>
        <div className="globe-placeholder">
          {/* Holographic globe visualization goes here */}
          <div className="holo-circle">
            <div className="holo-inner">Scanning...</div>
          </div>
        </div>
      </div>

      <div className="health-sidebar">
        <GuardianPanel onError={api.markError} onScan={api.runGuardianScan} />
        
        <div className="panel">
          <div className="panel-heading">
            <h2>Real-time Telemetry</h2>
          </div>
          <div className="telemetry-graphs">
            <div className="graph-row">
              <span>CPU</span>
              <div className="bar-bg"><div className="bar-fill" style={{ width: "34%" }}></div></div>
              <span>34%</span>
            </div>
            <div className="graph-row">
              <span>RAM</span>
              <div className="bar-bg"><div className="bar-fill" style={{ width: "62%" }}></div></div>
              <span>62%</span>
            </div>
            <div className="graph-row">
              <span>DISK IO</span>
              <div className="bar-bg"><div className="bar-fill" style={{ width: "12%" }}></div></div>
              <span>12%</span>
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-heading">
            <h2>Battery Health</h2>
          </div>
          <div className="battery-stats">
            <div className="metric">
              <span>Status</span>
              <strong>Healthy (92%)</strong>
            </div>
            <div className="metric">
              <span>Cycles</span>
              <strong>142</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
