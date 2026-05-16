import { BrainCircuit } from "lucide-react";
import { useState } from "react";
import type { BehaviorResponse } from "../services/jarvisApi";

interface BehaviorPanelProps {
  onPredict: () => Promise<BehaviorResponse>;
  onError: (message: string) => void;
}

export function BehaviorPanel({ onPredict, onError }: BehaviorPanelProps) {
  const [prediction, setPrediction] = useState<BehaviorResponse | null>(null);
  const [isPredicting, setIsPredicting] = useState(false);

  async function handlePredict() {
    setIsPredicting(true);
    try {
      setPrediction(await onPredict());
    } catch (error) {
      onError(error instanceof Error ? error.message : "Behavior prediction failed");
    } finally {
      setIsPredicting(false);
    }
  }

  return (
    <section className="panel behavior-panel" aria-labelledby="behavior-title">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Phase 5</p>
          <h2 id="behavior-title">Behavior Learning</h2>
        </div>
        <button disabled={isPredicting} onClick={handlePredict} type="button">
          <BrainCircuit size={16} />
          Predict
        </button>
      </div>
      <div className="result-list compact">
        {!prediction ? (
          <article className="notice">No prediction loaded.</article>
        ) : (
          <>
            <DataRow label="Prediction" value={prediction.prediction.predictedAction} />
            <DataRow label="Confidence" value={String(prediction.prediction.confidence)} />
            <DataRow label="Reason" value={prediction.prediction.reason} />
            <DataRow label="Enqueue" value={prediction.enqueue.reason} />
          </>
        )}
      </div>
    </section>
  );
}

interface DataRowProps {
  label: string;
  value: string;
}

function DataRow({ label, value }: DataRowProps) {
  return (
    <article className="data-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
