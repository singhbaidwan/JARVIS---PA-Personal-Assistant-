import { AvatarScene } from "../components/AvatarScene";
import { AssistantPanel } from "../components/AssistantPanel";
import { BehaviorPanel } from "../components/BehaviorPanel";
import { OperationsPanel } from "../components/OperationsPanel";
import type { JarvisApi } from "../hooks/useJarvisApi";

interface DashboardProps {
  api: JarvisApi;
}

export function Dashboard({ api }: DashboardProps) {
  return (
    <div className="page-content dashboard-layout">
      <AvatarScene mode={api.mode} status={api.status} />
      
      <section className="panel summary-panel" aria-labelledby="summary-title">
        <div className="panel-heading">
          <div>
            <p className="eyebrow">System</p>
            <h2 id="summary-title">Service State</h2>
          </div>
        </div>
        <div className="health-grid">
          <article className="metric">
            <span>Core</span>
            <strong>{api.health.core}</strong>
          </article>
          <article className="metric">
            <span>AI</span>
            <strong>{api.health.ai}</strong>
          </article>
          <article className="metric">
            <span>Mode</span>
            <strong>{api.mode}</strong>
          </article>
        </div>
      </section>

      <AssistantPanel onError={api.markError} onSendMessage={api.postAssistantChat} />
      <BehaviorPanel onError={api.markError} onPredict={api.runBehaviorPrediction} />
      <OperationsPanel fetchCoreList={api.fetchCoreList} fetchInsights={api.fetchInsights} onError={api.markError} />
    </div>
  );
}
