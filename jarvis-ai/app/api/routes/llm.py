import os

from fastapi import APIRouter, HTTPException

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.errors import LlmIntegrationError
from app.llm.local_client import generate_local_response
from app.llm.openai_client import generate_openai_response

router = APIRouter(tags=["llm"])


@router.post("/llm", response_model=LlmResponse)
def llm(request: LlmRequest) -> LlmResponse:
    try:
        provider = (request.provider or os.getenv("JARVIS_LLM_PROVIDER", "openai")).strip().lower()
        if provider in {"local", "ollama", "colima"}:
            return generate_local_response(request)
        if provider == "openai":
            return generate_openai_response(request)
        raise LlmIntegrationError(
            "Unsupported provider. Use `openai` or `local` (aliases: `ollama`, `colima`)."
        )
    except LlmIntegrationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
