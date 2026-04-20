from fastapi import APIRouter

from app.api.schemas import RecommendationRequest, RecommendationResponse
from app.intelligence import generate_recommendations

router = APIRouter(tags=["recommendations"])


@router.post("/recommendations", response_model=RecommendationResponse)
def recommendations(request: RecommendationRequest) -> RecommendationResponse:
    result = generate_recommendations(request.events, request.reference_time)
    return RecommendationResponse(
        focus_score=result.focus_score,
        peak_productivity_window=result.peak_productivity_window,
        summary=result.summary,
        recommendations=result.recommendations,
    )
