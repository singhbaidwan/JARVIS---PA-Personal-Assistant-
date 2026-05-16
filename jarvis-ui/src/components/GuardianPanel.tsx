import { ShieldAlert } from "lucide-react";
import { useState } from "react";
import type { GuardianResponse } from "../services/jarvisApi";

interface GuardianPanelProps {
  onScan: () => Promise<GuardianResponse>;
  onError: (message: string) => void;
}

export function GuardianPanel({ onScan, onError }: GuardianPanelProps) {
  const [scan, setScan] = useState<GuardianResponse | null>(null);
  const [isScanning, setIsScanning] = useState(false);

  async function handleScan() {
    setIsScanning(true);
    try {
      setScan(await onScan());
    } catch (error) {
      onError(error instanceof Error ? error.message : "Guardian scan failed");
    } finally {
      setIsScanning(false);
    }
  }

  const signals = scan?.signals?.length ? scan.signals : scan ? [scan.reason] : [];

  return (
    <section className="panel guardian-panel" aria-labelledby="guardian-title">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Phase 6</p>
          <h2 id="guardian-title">Guardian</h2>
        </div>
        <button disabled={isScanning} onClick={handleScan} type="button">
          <ShieldAlert size={16} />
          Scan
        </button>
      </div>
      <div className="score-row">
        <strong>{scan?.score ?? 0}</strong>
        <span>{scan?.severity ?? "NONE"}</span>
      </div>
      <div className="result-list compact">
        {signals.length === 0 ? (
          <article className="notice">No guardian scan loaded.</article>
        ) : (
          signals.map((signal) => (
            <article className="data-row" key={signal}>
              <span>Signal</span>
              <strong>{signal}</strong>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
