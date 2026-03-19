from fastapi import APIRouter

from app.api.schemas import LlmRequest, LlmResponse

router = APIRouter(tags=["llm"])


@router.post("/llm", response_model=LlmResponse)
def llm(request: LlmRequest) -> LlmResponse:
    return LlmResponse(response=f"[stub] received prompt: {request.prompt[:120]}")
