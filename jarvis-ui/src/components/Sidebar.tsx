import { Activity, LayoutDashboard, Settings, Network, Workflow, BrainCircuit } from "lucide-react";
import type { ReactElement } from "react";

export type TabID = "dashboard" | "health" | "knowledge" | "workflows" | "settings";

interface SidebarProps {
  activeTab: TabID;
  onTabChange: (tab: TabID) => void;
}

export function Sidebar({ activeTab, onTabChange }: SidebarProps) {
  const tabs: { id: TabID; label: string; icon: ReactElement }[] = [
    { id: "dashboard", label: "Dashboard", icon: <LayoutDashboard size={20} /> },
    { id: "health", label: "System Health", icon: <Activity size={20} /> },
    { id: "knowledge", label: "Knowledge", icon: <BrainCircuit size={20} /> },
    { id: "workflows", label: "Workflows", icon: <Workflow size={20} /> },
    { id: "settings", label: "Settings", icon: <Settings size={20} /> },
  ];

  return (
    <aside className="sidebar" aria-label="Main Navigation">
      <div className="sidebar-brand">
        <div className="sidebar-logo">J</div>
        <div className="sidebar-title">
          <span className="eyebrow">Project</span>
          <h2>JARVIS</h2>
        </div>
      </div>
      <nav className="sidebar-nav">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            className={`nav-item ${activeTab === tab.id ? "active" : ""}`}
            onClick={() => onTabChange(tab.id)}
            aria-current={activeTab === tab.id ? "page" : undefined}
          >
            {tab.icon}
            <span>{tab.label}</span>
          </button>
        ))}
      </nav>
      <div className="sidebar-footer">
        <div className="status-indicator">
          <div className="pulse-dot"></div>
          <span>System Online</span>
        </div>
      </div>
    </aside>
  );
}
