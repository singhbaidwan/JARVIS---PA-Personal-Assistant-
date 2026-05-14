from fastapi import APIRouter, HTTPException

from app.api.schemas import AssistantChatRequest, AssistantChatResponse, LlmRequest
from app.assistant import build_assistant_prompt, extract_action_suggestions, summarize_context
from app.llm.errors import LlmIntegrationError
from app.llm.provider_router import generator_for_provider, resolve_provider

router = APIRouter(tags=["assistant"])


@router.post("/assistant/chat", response_model=AssistantChatResponse)
def assistant_chat(request: AssistantChatRequest) -> AssistantChatResponse:
    system_prompt, prompt = build_assistant_prompt(request.message, request.context)
    llm_request = LlmRequest(
        prompt=prompt,
        provider=request.provider,
        system_prompt=system_prompt,
        model=request.model,
        temperature=request.temperature,
        max_tokens=request.max_tokens,
    )

    try:
        provider = resolve_provider(llm_request)
        generator = generator_for_provider(provider)
        llm_response = generator(llm_request)
    except LlmIntegrationError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error

    return AssistantChatResponse(
        response=llm_response.response,
        model=llm_response.model,
        provider=llm_response.provider,
        suggested_actions=extract_action_suggestions(llm_response.response),
        context_summary=summarize_context(request.context),
        metadata=llm_response.metadata,
    )
