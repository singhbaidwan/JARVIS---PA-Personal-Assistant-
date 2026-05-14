from __future__ import annotations

import json
import re
from typing import Any

from app.api.schemas import AssistantActionSuggestion, AssistantContext


def build_assistant_prompt(message: str, context: AssistantContext) -> tuple[str, str]:
    context_summary = summarize_context(context)
    system_prompt = (
        "You are JARVIS, a concise local-first personal assistant. Use the supplied context "
        "when it is relevant. You may suggest actions, but you never claim an action has been "
        "executed. Core will decide whether suggestions are safe.\n\n"
        "When an action would help, include a compact JSON block with this shape:\n"
        '{"suggested_actions":[{"action":"OPEN_APP|CLOSE_APP|SEARCH_FILES",'
        '"app":"optional app","query":"optional search query","confidence":0.0,'
        '"reason":"short reason"}]}'
    )
    prompt = f"Context:\n{context_summary}\n\nUser message:\n{message.strip()}"
    return system_prompt, prompt


def summarize_context(context: AssistantContext) -> str:
    parts: list[str] = []
    if context.recent_events:
        formatted_events = []
        for event in context.recent_events[-8:]:
            timestamp = event.timestamp.isoformat() if event.timestamp else "unknown-time"
            formatted_events.append(f"{timestamp} {event.type} {event.payload}")
        parts.append("Recent events:\n" + "\n".join(formatted_events))

    if context.search_results:
        formatted_results = []
        for result in context.search_results[:5]:
            formatted_results.append(
                f"{result.name} ({result.path}) score={result.score} reason={result.reason}"
            )
        parts.append("Search results:\n" + "\n".join(formatted_results))

    if context.facts:
        facts = ", ".join(f"{key}={value}" for key, value in sorted(context.facts.items()))
        parts.append(f"Facts: {facts}")

    return "\n\n".join(parts) if parts else "No additional context supplied."


def extract_action_suggestions(text: str) -> list[AssistantActionSuggestion]:
    suggestions: list[AssistantActionSuggestion] = []
    seen: set[tuple[str, str | None, str | None, str]] = set()
    for candidate in _json_candidates(text):
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue

        raw_actions = parsed.get("suggested_actions") if isinstance(parsed, dict) else None
        if not isinstance(raw_actions, list):
            continue

        for raw_action in raw_actions:
            suggestion = _coerce_suggestion(raw_action)
            if suggestion is None:
                continue

            signature = (suggestion.action, suggestion.app, suggestion.query, suggestion.reason)
            if signature not in seen:
                seen.add(signature)
                suggestions.append(suggestion)

    return suggestions


def _json_candidates(text: str) -> list[str]:
    candidates = re.findall(r"```(?:json)?\s*(\{.*?\})\s*```", text, flags=re.DOTALL | re.IGNORECASE)
    stripped = text.strip()
    if stripped.startswith("{") and stripped.endswith("}"):
        candidates.append(stripped)

    object_match = re.search(r"(\{\s*\"suggested_actions\"\s*:\s*\[.*?\]\s*\})", text, flags=re.DOTALL)
    if object_match:
        candidates.append(object_match.group(1))
    return candidates


def _coerce_suggestion(raw_action: Any) -> AssistantActionSuggestion | None:
    if not isinstance(raw_action, dict):
        return None

    action = _clean_string(raw_action.get("action"))
    reason = _clean_string(raw_action.get("reason")) or "Suggested by assistant"
    if not action:
        return None

    confidence = raw_action.get("confidence", 0.0)
    if not isinstance(confidence, (int, float)):
        confidence = 0.0

    return AssistantActionSuggestion(
        action=action.upper(),
        app=_clean_string(raw_action.get("app")),
        query=_clean_string(raw_action.get("query")),
        confidence=max(0.0, min(float(confidence), 1.0)),
        reason=reason,
    )


def _clean_string(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    cleaned = value.strip()
    return cleaned or None
