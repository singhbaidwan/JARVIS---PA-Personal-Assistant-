import { Send, ShieldCheck, ShieldX } from "lucide-react";
import { useState } from "react";
import type { AssistantAction, AssistantChatResponse } from "../services/jarvisApi";

interface AssistantPanelProps {
  onSendMessage: (message: string, provider: string) => Promise<AssistantChatResponse>;
  onError: (message: string) => void;
}

interface ChatMessage {
  id: string;
  role: "user" | "assistant" | "error";
  text: string;
}

export function AssistantPanel({ onSendMessage, onError }: AssistantPanelProps) {
  const [provider, setProvider] = useState("offline");
  const [message, setMessage] = useState("Find the Python file I edited yesterday, then suggest what to open.");
  const [chat, setChat] = useState<ChatMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      text: "Console online. Use offline provider for a local smoke test or choose a configured LLM provider.",
    },
  ]);
  const [suggestions, setSuggestions] = useState<AssistantAction[]>([]);
  const [isSending, setIsSending] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = message.trim();
    if (!trimmed) {
      return;
    }

    setChat((current) => [...current, { id: crypto.randomUUID(), role: "user", text: trimmed }]);
    setMessage("");
    setSuggestions([]);
    setIsSending(true);
    try {
      const response = await onSendMessage(trimmed, provider);
      setChat((current) => [...current, { id: crypto.randomUUID(), role: "assistant", text: response.response }]);
      setSuggestions(response.suggestedActions ?? []);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "Assistant request failed";
      setChat((current) => [...current, { id: crypto.randomUUID(), role: "error", text: errorMessage }]);
      onError(errorMessage);
    } finally {
      setIsSending(false);
    }
  }

  return (
    <section className="panel chat-panel" aria-labelledby="chat-title">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Phase 8</p>
          <h2 id="chat-title">Assistant Chat</h2>
        </div>
        <select value={provider} onChange={(event) => setProvider(event.target.value)} aria-label="LLM provider">
          <option value="offline">offline</option>
          <option value="local">local</option>
          <option value="openai">openai</option>
          <option value="claude">claude</option>
          <option value="gemini">gemini</option>
        </select>
      </div>

      <div className="chat-log" aria-live="polite">
        {chat.map((entry) => (
          <article className={`message ${entry.role}`} key={entry.id}>
            {entry.text}
          </article>
        ))}
      </div>

      <form className="composer" onSubmit={handleSubmit}>
        <textarea value={message} onChange={(event) => setMessage(event.target.value)} rows={3} />
        <button disabled={isSending} type="submit">
          <Send size={16} />
          Send
        </button>
      </form>

      <div className="result-list compact">
        {suggestions.length === 0 ? (
          <article className="notice">No suggested actions returned.</article>
        ) : (
          suggestions.map((action) => <SuggestionRow action={action} key={`${action.action}-${action.reason}`} />)
        )}
      </div>
    </section>
  );
}

interface SuggestionRowProps {
  action: AssistantAction;
}

function SuggestionRow({ action }: SuggestionRowProps) {
  const Icon = action.approved ? ShieldCheck : ShieldX;
  return (
    <article className={`action-row ${action.approved ? "approved" : "blocked"}`}>
      <Icon size={18} />
      <div>
        <strong>{[action.action, action.app ?? action.query].filter(Boolean).join(" ")}</strong>
        <span>
          {action.approved ? "approved" : "blocked"} · {Math.round(action.confidence * 100)}%
        </span>
        <p>{action.reason}</p>
      </div>
    </article>
  );
}
