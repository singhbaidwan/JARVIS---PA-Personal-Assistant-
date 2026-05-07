from __future__ import annotations

import json
import os
from typing import Any
import urllib.error
import urllib.request

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.errors import LlmIntegrationError


def generate_local_response(
    request: LlmRequest,
    provider_label: str = "local",
    default_model_env: str = "LOCAL_LLM_MODEL",
) -> LlmResponse:
    base_url = os.getenv("LOCAL_LLM_BASE_URL", "http://127.0.0.1:11434").rstrip("/")
    model = request.model or os.getenv(default_model_env) or os.getenv("LOCAL_LLM_MODEL", "llama3.2:latest")
    endpoint = f"{base_url}/api/chat"

    payload = {
        "model": model,
        "messages": _messages_for_request(request),
        "stream": False,
        "options": {
            "temperature": request.temperature,
        },
    }
    body = json.dumps(payload).encode("utf-8")
    http_request = urllib.request.Request(
        endpoint,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(http_request, timeout=60) as response:
            response_json = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="ignore")
        raise LlmIntegrationError(f"Local LLM request failed ({error.code}): {detail[:240]}") from error
    except urllib.error.URLError as error:
        raise LlmIntegrationError(
            "Local LLM connection failed. Make sure Colima and Ollama are running and "
            f"`{base_url}` is reachable ({error.reason})."
        ) from error
    except TimeoutError as error:
        raise LlmIntegrationError("Local LLM request timed out") from error
    except json.JSONDecodeError as error:
        raise LlmIntegrationError("Local LLM response was not valid JSON") from error

    message = response_json.get("message")
    if not isinstance(message, dict):
        raise LlmIntegrationError("Local LLM response did not include a message object")
    content = message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise LlmIntegrationError("Local LLM response did not include assistant text")

    metadata: dict[str, Any] = {}
    for key in ("done_reason", "prompt_eval_count", "eval_count", "total_duration"):
        value = response_json.get(key)
        if value is not None:
            metadata[key] = value

    return LlmResponse(
        response=content.strip(),
        model=str(response_json.get("model", model)),
        provider=provider_label,
        metadata=metadata,
    )


def _messages_for_request(request: LlmRequest) -> list[dict[str, str]]:
    messages: list[dict[str, str]] = []
    if request.system_prompt:
        messages.append({"role": "system", "content": request.system_prompt})
    messages.append({"role": "user", "content": request.prompt})
    return messages
