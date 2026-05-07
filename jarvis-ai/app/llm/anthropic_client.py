from __future__ import annotations

import json
import os
from typing import Any
import urllib.error
import urllib.request

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.errors import LlmIntegrationError


def generate_anthropic_response(request: LlmRequest) -> LlmResponse:
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        raise LlmIntegrationError("ANTHROPIC_API_KEY is not configured")

    model = request.model or os.getenv("ANTHROPIC_MODEL", "claude-3-5-sonnet-latest")
    base_url = os.getenv("ANTHROPIC_API_BASE_URL", "https://api.anthropic.com/v1").rstrip("/")
    endpoint = f"{base_url}/messages"
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": request.prompt}],
        "max_tokens": request.max_tokens,
        "temperature": request.temperature,
    }
    if request.system_prompt:
        payload["system"] = request.system_prompt

    body = json.dumps(payload).encode("utf-8")
    http_request = urllib.request.Request(
        endpoint,
        data=body,
        headers={
            "Content-Type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": os.getenv("ANTHROPIC_API_VERSION", "2023-06-01"),
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(http_request, timeout=30) as response:
            response_json = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="ignore")
        raise LlmIntegrationError(f"Anthropic request failed ({error.code}): {detail[:240]}") from error
    except urllib.error.URLError as error:
        raise LlmIntegrationError(f"Anthropic connection failed: {error.reason}") from error
    except TimeoutError as error:
        raise LlmIntegrationError("Anthropic request timed out") from error
    except json.JSONDecodeError as error:
        raise LlmIntegrationError("Anthropic response was not valid JSON") from error

    text = _extract_text(response_json)
    if not text:
        raise LlmIntegrationError("Anthropic response did not include assistant text")

    metadata: dict[str, Any] = {}
    usage = response_json.get("usage")
    if isinstance(usage, dict):
        metadata["usage"] = usage
    stop_reason = response_json.get("stop_reason")
    if stop_reason:
        metadata["stop_reason"] = stop_reason

    return LlmResponse(
        response=text,
        model=str(response_json.get("model", model)),
        provider="claude",
        metadata=metadata,
    )


def _extract_text(response_json: dict[str, Any]) -> str:
    content = response_json.get("content")
    if not isinstance(content, list):
        return ""

    parts: list[str] = []
    for item in content:
        if not isinstance(item, dict):
            continue
        if item.get("type") != "text":
            continue
        text = item.get("text")
        if isinstance(text, str) and text.strip():
            parts.append(text.strip())
    return "\n".join(parts)
