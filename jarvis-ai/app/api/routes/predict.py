from fastapi import APIRouter

from app.api.schemas import PredictRequest, PredictResponse
from app.intelligence import predict_next_action

router = APIRouter(tags=["predict"])


@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    result = predict_next_action(request.events, request.reference_time)
    return PredictResponse(
        predicted_action=result.predicted_action,
        confidence=result.confidence,
        reason=result.reason,
        context=result.context,
    )
