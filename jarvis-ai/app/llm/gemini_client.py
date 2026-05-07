from __future__ import annotations

import json
import os
from typing import Any
import urllib.error
import urllib.parse
import urllib.request

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.errors import LlmIntegrationError


def generate_gemini_response(request: LlmRequest) -> LlmResponse:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise LlmIntegrationError("GEMINI_API_KEY is not configured")

    model = request.model or os.getenv("GEMINI_MODEL", "gemini-1.5-flash")
    base_url = os.getenv(
        "GEMINI_API_BASE_URL", "https://generativelanguage.googleapis.com/v1beta"
    ).rstrip("/")
    endpoint = f"{base_url}/models/{model}:generateContent?key={urllib.parse.quote(api_key)}"

    payload: dict[str, Any] = {
        "contents": [
            {
                "role": "user",
                "parts": [{"text": request.prompt}],
            }
        ],
        "generationConfig": {
            "temperature": request.temperature,
            "maxOutputTokens": request.max_tokens,
        },
    }
    if request.system_prompt:
        payload["systemInstruction"] = {
            "parts": [{"text": request.system_prompt}],
        }

    body = json.dumps(payload).encode("utf-8")
    http_request = urllib.request.Request(
        endpoint,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(http_request, timeout=30) as response:
            response_json = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="ignore")
        raise LlmIntegrationError(f"Gemini request failed ({error.code}): {detail[:240]}") from error
    except urllib.error.URLError as error:
        raise LlmIntegrationError(f"Gemini connection failed: {error.reason}") from error
    except TimeoutError as error:
        raise LlmIntegrationError("Gemini request timed out") from error
    except json.JSONDecodeError as error:
        raise LlmIntegrationError("Gemini response was not valid JSON") from error

    text = _extract_text(response_json)
    if not text:
        raise LlmIntegrationError("Gemini response did not include assistant text")

    metadata: dict[str, Any] = {}
    usage = response_json.get("usageMetadata")
    if isinstance(usage, dict):
        metadata["usage"] = usage

    return LlmResponse(
        response=text,
        model=model,
        provider="gemini",
        metadata=metadata,
    )


def _extract_text(response_json: dict[str, Any]) -> str:
    candidates = response_json.get("candidates")
    if not isinstance(candidates, list) or not candidates:
        return ""

    first = candidates[0]
    if not isinstance(first, dict):
        return ""
    content = first.get("content")
    if not isinstance(content, dict):
        return ""
    parts = content.get("parts")
    if not isinstance(parts, list):
        return ""

    texts: list[str] = []
    for part in parts:
        if not isinstance(part, dict):
            continue
        text = part.get("text")
        if isinstance(text, str) and text.strip():
            texts.append(text.strip())
    return "\n".join(texts)
