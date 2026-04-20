from __future__ import annotations

import json
import os
from typing import Any
import urllib.error
import urllib.request

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.errors import LlmIntegrationError

def generate_openai_response(request: LlmRequest) -> LlmResponse:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise LlmIntegrationError("OPENAI_API_KEY is not configured")

    model = request.model or os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    base_url = os.getenv("OPENAI_API_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    endpoint = f"{base_url}/chat/completions"
    payload = {
        "model": model,
        "messages": _messages_for_request(request),
        "temperature": request.temperature,
        "max_tokens": request.max_tokens,
    }

    body = json.dumps(payload).encode("utf-8")
    http_request = urllib.request.Request(
        endpoint,
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(http_request, timeout=30) as response:
            response_json = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="ignore")
        raise LlmIntegrationError(f"OpenAI request failed ({error.code}): {detail[:240]}") from error
    except urllib.error.URLError as error:
        raise LlmIntegrationError(f"OpenAI connection failed: {error.reason}") from error
    except TimeoutError as error:
        raise LlmIntegrationError("OpenAI request timed out") from error
    except json.JSONDecodeError as error:
        raise LlmIntegrationError("OpenAI response was not valid JSON") from error

    text = _extract_text(response_json)
    if not text:
        raise LlmIntegrationError("OpenAI response did not include assistant text")

    usage = response_json.get("usage")
    metadata: dict[str, Any] = {}
    if isinstance(usage, dict):
        metadata["usage"] = usage

    return LlmResponse(
        response=text,
        model=str(response_json.get("model", model)),
        provider="openai",
        metadata=metadata,
    )


def _messages_for_request(request: LlmRequest) -> list[dict[str, str]]:
    messages: list[dict[str, str]] = []
    if request.system_prompt:
        messages.append({"role": "system", "content": request.system_prompt})
    messages.append({"role": "user", "content": request.prompt})
    return messages


def _extract_text(response_json: dict[str, Any]) -> str:
    choices = response_json.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""

    first_choice = choices[0]
    if not isinstance(first_choice, dict):
        return ""

    message = first_choice.get("message")
    if not isinstance(message, dict):
        return ""

    content = message.get("content")
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if not isinstance(item, dict):
                continue
            if item.get("type") != "text":
                continue
            text = item.get("text")
            if isinstance(text, str):
                parts.append(text)
        return "\n".join(part.strip() for part in parts if part.strip())
    return ""
