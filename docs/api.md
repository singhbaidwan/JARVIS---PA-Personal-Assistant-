# API

## jarvis-core
- `POST /event`
- `GET /event`
- `GET /insights/daily`
- `GET /health`
- `POST /workflow` (supports DAG via step `dependsOn`)
- `GET /workflow`
- `GET /workflow/{id}`

## jarvis-ai
- `GET /health`
- `POST /llm` (`provider=openai` or `provider=local` for Ollama/Colima)
- `POST /predict`
- `POST /anomaly`
- `POST /recommendations`
