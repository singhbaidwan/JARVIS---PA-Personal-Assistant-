from __future__ import annotations

import os
from collections.abc import Callable

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.anthropic_client import generate_anthropic_response
from app.llm.errors import LlmIntegrationError
from app.llm.gemini_client import generate_gemini_response
from app.llm.local_client import generate_local_response
from app.llm.offline_client import generate_offline_response
from app.llm.openai_client import generate_openai_response

LlmGenerator = Callable[[LlmRequest], LlmResponse]


def resolve_provider(request: LlmRequest) -> str:
    return (request.provider or os.getenv("JARVIS_LLM_PROVIDER", "openai")).strip().lower()


def generator_for_provider(provider: str) -> LlmGenerator:
    if provider == "openai":
        return generate_openai_response
    if provider in {"claude", "anthropic"}:
        return generate_anthropic_response
    if provider in {"gemini", "google"}:
        return generate_gemini_response
    if provider in {"local", "ollama", "colima"}:
        return lambda request: generate_local_response(request, provider_label="local")
    if provider in {"llama", "llm-a", "llma"}:
        return lambda request: generate_local_response(
            request,
            provider_label="llama",
            default_model_env="LLAMA_MODEL",
        )
    if provider in {"offline", "rules", "dev"}:
        return generate_offline_response

    raise LlmIntegrationError(
        "Unsupported provider. Use one of: openai, claude/anthropic, gemini/google, "
        "ollama/local/colima, llama, offline/rules."
    )
