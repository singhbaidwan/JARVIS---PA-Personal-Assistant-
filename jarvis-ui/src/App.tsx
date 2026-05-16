import { useEffect } from "react";
import { AssistantPanel } from "./components/AssistantPanel";
import { AvatarScene } from "./components/AvatarScene";
import { BehaviorPanel } from "./components/BehaviorPanel";
import { GuardianPanel } from "./components/GuardianPanel";
import { OperationsPanel } from "./components/OperationsPanel";
import { SearchPanel } from "./components/SearchPanel";
import { TopBar } from "./components/TopBar";
import { useJarvisApi } from "./hooks/useJarvisApi";

export function App() {
  const api = useJarvisApi();

  useEffect(() => {
    api.refreshHealth().catch((error: unknown) => {
      api.markError(error instanceof Error ? error.message : "Health check failed");
    });
  }, [api.refreshHealth, api.markError]);

  return (
    <div className="app-shell">
      <TopBar
        endpoints={api.endpoints}
        health={api.health}
        onEndpointsChange={api.setEndpoints}
        onRefreshHealth={() => {
          api.refreshHealth().catch((error: unknown) => {
            api.markError(error instanceof Error ? error.message : "Health check failed");
          });
        }}
      />
      <main className="dashboard">
        <AvatarScene mode={api.mode} status={api.status} />
        <section className="panel health-panel" aria-labelledby="health-title">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">System</p>
              <h2 id="health-title">Service State</h2>
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
        <SearchPanel onError={api.markError} onSearch={api.runSearch} />
        <GuardianPanel onError={api.markError} onScan={api.runGuardianScan} />
        <BehaviorPanel onError={api.markError} onPredict={api.runBehaviorPrediction} />
        <OperationsPanel fetchCoreList={api.fetchCoreList} fetchInsights={api.fetchInsights} onError={api.markError} />
      </main>
    </div>
  );
}
