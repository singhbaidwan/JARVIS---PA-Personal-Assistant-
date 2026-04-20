from fastapi import APIRouter

from app.api.schemas import AnomalyRequest, AnomalyResponse
from app.intelligence import detect_anomalies

router = APIRouter(tags=["anomaly"])


@router.post("/anomaly", response_model=AnomalyResponse)
def anomaly(request: AnomalyRequest) -> AnomalyResponse:
    result = detect_anomalies(request.events, request.reference_time)
    return AnomalyResponse(
        anomaly_detected=result.anomaly_detected,
        reason=result.reason,
        severity=result.severity,
        score=result.score,
        signals=result.signals,
    )
