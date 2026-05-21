import { useEffect, useState } from "react";
import { Sidebar, type TabID } from "./components/Sidebar";
import { Dashboard } from "./pages/Dashboard";
import { SystemHealth } from "./pages/SystemHealth";
import { Knowledge } from "./pages/Knowledge";
import { Workflows } from "./pages/Workflows";
import { Settings } from "./pages/Settings";
import { useJarvisApi } from "./hooks/useJarvisApi";
import { TopBar } from "./components/TopBar";

export function App() {
  const api = useJarvisApi();
  const [activeTab, setActiveTab] = useState<TabID>("dashboard");

  useEffect(() => {
    api.refreshHealth().catch((error: unknown) => {
      api.markError(error instanceof Error ? error.message : "Health check failed");
    });
  }, [api.refreshHealth, api.markError]);

  const renderContent = () => {
    switch (activeTab) {
      case "dashboard":
        return <Dashboard api={api} />;
      case "health":
        return <SystemHealth api={api} />;
      case "knowledge":
        return <Knowledge api={api} />;
      case "workflows":
        return <Workflows api={api} />;
      case "settings":
        return <Settings api={api} />;
      default:
        return <Dashboard api={api} />;
    }
  };

  return (
    <div className="app-shell">
      <Sidebar activeTab={activeTab} onTabChange={setActiveTab} />
      <div className="main-container">
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
        <main className="main-content">
          {renderContent()}
        </main>
      </div>
    </div>
  );
}
