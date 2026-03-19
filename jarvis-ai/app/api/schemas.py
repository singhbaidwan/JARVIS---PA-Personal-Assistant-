from pydantic import BaseModel, Field


class EventInput(BaseModel):
    type: str
    payload: dict = Field(default_factory=dict)
    source: str | None = None


class PredictRequest(BaseModel):
    events: list[EventInput] = Field(default_factory=list)


class PredictResponse(BaseModel):
    predicted_action: str
    confidence: float


class LlmRequest(BaseModel):
    prompt: str


class LlmResponse(BaseModel):
    response: str


class AnomalyRequest(BaseModel):
    events: list[EventInput] = Field(default_factory=list)


class AnomalyResponse(BaseModel):
    anomaly_detected: bool
    reason: str
