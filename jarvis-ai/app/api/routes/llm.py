from fastapi import APIRouter, HTTPException

from app.api.schemas import LlmRequest, LlmResponse
from app.llm.errors import LlmIntegrationError
from app.llm.provider_router import generator_for_provider, resolve_provider

router = APIRouter(tags=["llm"])


@router.post("/llm", response_model=LlmResponse)
def llm(request: LlmRequest) -> LlmResponse:
    try:
        provider = resolve_provider(request)
        generator = generator_for_provider(provider)
        return generator(request)
    except LlmIntegrationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
