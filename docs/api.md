# API

## jarvis-core
- `POST /event`
- `GET /event`
- `GET /insights/daily`
- `GET /health`
- `POST /workflow` (supports DAG via step `dependsOn`)
- `GET /workflow`
- `GET /workflow/{id}`
- `POST /behavior-learning/predict` (runs core behavior-learning flow and can enqueue safe actions)
- `POST /guardian/anomaly` (runs core-to-AI anomaly scan over recent events)
- `POST /search` (runs core-to-AI local file search)

## jarvis-ai
- `GET /health`
- `POST /llm` (`provider=openai|claude|gemini|ollama|llama|local`)
- `POST /predict`
- `POST /anomaly`
- `POST /recommendations`
- `POST /search`
