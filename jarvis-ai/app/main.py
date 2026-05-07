from fastapi import FastAPI

from app.api.routes.anomaly import router as anomaly_router
from app.api.routes.health import router as health_router
from app.api.routes.llm import router as llm_router
from app.api.routes.predict import router as predict_router
from app.api.routes.recommendations import router as recommendations_router
from app.api.routes.search import router as search_router

app = FastAPI(title="jarvis-ai", version="0.1.0")
app.include_router(health_router)
app.include_router(llm_router)
app.include_router(predict_router)
app.include_router(anomaly_router)
app.include_router(recommendations_router)
app.include_router(search_router)
