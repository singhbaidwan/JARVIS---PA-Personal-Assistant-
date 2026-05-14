from __future__ import annotations

import re

from app.api.schemas import LlmRequest, LlmResponse


def generate_offline_response(request: LlmRequest) -> LlmResponse:
    prompt = request.prompt.strip()
    user_message = _extract_user_message(prompt)
    suggestion_json = _suggestion_json(user_message)
    response = (
        "I can help with that. I will only suggest the next step; core must approve it before anything happens."
    )
    if suggestion_json:
        response = f"{response}\n{suggestion_json}"

    return LlmResponse(
        response=response,
        model="jarvis-offline-rules",
        provider="offline",
        metadata={"mode": "deterministic-dev-provider"},
    )


def _extract_user_message(prompt: str) -> str:
    marker = "User message:"
    if marker not in prompt:
        return prompt
    return prompt.split(marker, 1)[1].strip()


def _suggestion_json(message: str) -> str:
    lowered = message.lower()
    if any(word in lowered for word in ("find", "search", "locate")):
        query = _clean_query(message)
        return (
            '{"suggested_actions":[{"action":"SEARCH_FILES",'
            f'"query":"{_escape_json_string(query)}","confidence":0.72,'
            '"reason":"The user asked to find local information"}]}'
        )

    app = _app_from_message(message)
    if app:
        return (
            '{"suggested_actions":[{"action":"OPEN_APP",'
            f'"app":"{_escape_json_string(app)}","confidence":0.68,'
            '"reason":"The user asked to open an app"}]}'
        )

    return ""


def _clean_query(message: str) -> str:
    cleaned = re.sub(r"\b(find|search|locate|for|please)\b", " ", message, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    return cleaned or message.strip()


def _app_from_message(message: str) -> str | None:
    match = re.search(r"\bopen\s+([A-Za-z][A-Za-z0-9 ._-]{1,40})", message, flags=re.IGNORECASE)
    if not match:
        return None
    return match.group(1).strip().rstrip(".")


def _escape_json_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')
