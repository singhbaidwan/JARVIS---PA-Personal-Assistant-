from fastapi import APIRouter

from app.api.schemas import PredictRequest, PredictResponse

router = APIRouter(tags=["predict"])


@router.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    has_code_activity = any(event.payload.get("to") == "Xcode" for event in request.events)
    action = "OPEN_XCODE" if has_code_activity else "NO_ACTION"
    confidence = 0.65 if has_code_activity else 0.25
    return PredictResponse(predicted_action=action, confidence=confidence)
