from fastapi import APIRouter

from app.api.schemas import AnomalyRequest, AnomalyResponse

router = APIRouter(tags=["anomaly"])


@router.post("/anomaly", response_model=AnomalyResponse)
def anomaly(request: AnomalyRequest) -> AnomalyResponse:
    event_count = len(request.events)
    if event_count > 1000:
        return AnomalyResponse(anomaly_detected=True, reason="High event volume spike")
    return AnomalyResponse(anomaly_detected=False, reason="No anomaly detected")
