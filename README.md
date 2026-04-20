# JARVIS - Personal Assistant (Phase 0 + Phase 1 + Phase 2 + Phase 3 + Phase 4)

This repository now includes:
- Phase 0 monorepo setup
- Phase 1 event pipeline foundation
- Phase 2 daily insights engine (`/insights/daily` + CLI output)
- Phase 3 command execution layer (`/command` queue + agent execution)
- Phase 4 task + intelligence + assistant layer:
  - DAG workflow execution with parallel roots (`dependsOn`)
  - AI intelligence APIs (`/predict`, `/anomaly`, `/recommendations`)
  - LLM endpoint (`/llm`) with OpenAI integration
  - Avatar UI starter (`jarvis-ui/public`)

## Monorepo Layout

- `jarvis-core` - Kotlin + Spring Boot core service (events, insights, command queue)
- `jarvis-agent` - Swift macOS agent (app monitoring + command execution)
- `jarvis-ai` - Python FastAPI AI service scaffold
- `jarvis-runtime` - Process manager scripts
- `jarvis-ui` - Avatar UI web console
- `jarvis-data` - local DB/migrations/vector storage directories
- `logs` - service logs

## Core APIs

- `POST /event`
- `GET /event`
- `GET /insights/daily`
- `POST /command`
- `GET /command`
- `POST /command/claim`
- `POST /command/{id}/result`
- `POST /workflow`
- `GET /workflow`
- `GET /workflow/{id}`
- `GET /health`
- `POST /llm`
- `POST /predict`
- `POST /anomaly`
- `POST /recommendations`

## Agent Configuration

Preferred base URL:

```bash
export JARVIS_CORE_BASE_URL=http://127.0.0.1:8080
```

Optional:

```bash
export JARVIS_AGENT_ID=jarvis-agent
export JARVIS_COMMAND_POLL_INTERVAL_SECONDS=3
export JARVIS_COMMAND_WORKER_COUNT=2
export JARVIS_COMMAND_EXECUTION_TIMEOUT_SECONDS=15
```

Core lease recovery (optional):

```bash
export JARVIS_COMMAND_CLAIM_TIMEOUT_SECONDS=30
```

Legacy `JARVIS_CORE_EVENT_URL` is still supported.

## Runtime Scripts

- `scripts/start_all.sh`
- `scripts/stop_all.sh`
- `scripts/status.sh`
- `scripts/insights.sh`
- `scripts/reset_db.sh`
- `scripts/tail_logs.sh`

## Verify Phase 3 End-to-End

From repo root:

1. Start all services:

```bash
./scripts/start_all.sh
```

2. Confirm service status:

```bash
./scripts/status.sh
```

3. Queue a command:

```bash
curl -X POST http://127.0.0.1:8080/command \
  -H "Content-Type: application/json" \
  -d '{"action":"OPEN_APP","app":"Calculator","priority":3}'
```

Note: `POST /command` only enqueues work. The `jarvis-agent` process must be running to claim and execute it.

4. Check command state transitions:

```bash
curl http://127.0.0.1:8080/command
```

Expected lifecycle: `QUEUED` -> `IN_PROGRESS` -> `SUCCEEDED` (or retry/fail).

If a command is claimed but never completed (for example, an agent crash), core automatically recovers stale claims after the configured timeout.

5. Optional retry + rollback example:

```bash
curl -X POST http://127.0.0.1:8080/command \
  -H "Content-Type: application/json" \
  -d '{
    "action":"OPEN_APP",
    "app":"NotARealApp",
    "maxAttempts":2,
    "rollbackAction":"OPEN_APP",
    "rollbackParams":{"app":"Finder"}
  }'
```

6. Queue a conditional multi-step workflow:

```bash
curl -X POST http://127.0.0.1:8080/workflow \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Low battery safeguard",
    "context": {"battery": 15},
    "condition": {"leftKey": "battery", "operator": "LT", "rightValue": 20},
    "steps": [
      {"action": "OPEN_APP", "app": "Calculator", "priority": 3},
      {"action": "CLOSE_APP", "app": "Calculator", "priority": 3}
    ]
  }'
```

7. Check workflow status:

```bash
curl http://127.0.0.1:8080/workflow
```

Expected workflow lifecycle: `IN_PROGRESS` -> `SUCCEEDED` (or `FAILED` / `SKIPPED`).

8. Stop services:

```bash
./scripts/stop_all.sh
```

## Verify Phase 4 End-to-End

From repo root:

1. Start services:

```bash
./scripts/start_all.sh
./scripts/status.sh
```

2. Test DAG + parallel workflow roots (`step 1` and `step 2` start together, `step 3` waits):

```bash
curl -X POST http://127.0.0.1:8080/workflow \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Start Work Parallel",
    "steps": [
      {"action": "OPEN_APP", "app": "VS Code", "dependsOn": []},
      {"action": "OPEN_APP", "app": "Chrome", "dependsOn": []},
      {"action": "OPEN_APP", "app": "Slack", "dependsOn": [1, 2]}
    ]
  }'
```

3. Inspect workflow state:

```bash
curl http://127.0.0.1:8080/workflow
```

4. Test intelligence endpoints:

```bash
curl -X POST http://127.0.0.1:8000/predict \
  -H "Content-Type: application/json" \
  -d '{"events":[{"type":"APP_SWITCH","timestamp":"2026-04-11T09:00:00Z","payload":{"to":"VS Code"}}]}'
```

```bash
curl -X POST http://127.0.0.1:8000/anomaly \
  -H "Content-Type: application/json" \
  -d '{"events":[{"type":"RESOURCE_SAMPLE","payload":{"cpu_percent":91}}]}'
```

```bash
curl -X POST http://127.0.0.1:8000/recommendations \
  -H "Content-Type: application/json" \
  -d '{"events":[{"type":"APP_SWITCH","payload":{"to":"YouTube"}},{"type":"APP_SWITCH","payload":{"to":"VS Code"}}]}'
```

5. Test LLM endpoint with OpenAI:

```bash
export OPENAI_API_KEY="<your-key>"
curl -X POST http://127.0.0.1:8000/llm \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Plan my next 2 hours for deep work."}'
```

6. Test LLM endpoint with local model (Ollama on Colima):

```bash
colima start
docker run -d --name ollama -p 11434:11434 ollama/ollama
docker exec -it ollama ollama pull llama3.2:latest
```

```bash
curl -X POST http://127.0.0.1:8000/llm \
  -H "Content-Type: application/json" \
  -d '{
    "provider":"local",
    "model":"llama3.2:latest",
    "prompt":"Plan my next 2 hours for deep work."
  }'
```

7. Run avatar UI:

```bash
cd jarvis-ui
npm run dev
```

Open `http://127.0.0.1:5173`, confirm the API URL points to `http://127.0.0.1:8000/llm`, then send a prompt.

## Testing

Kotlin core tests:

```bash
cd jarvis-core
./gradlew test
cd ..
```

Swift build:

```bash
cd jarvis-agent
swift build
cd ..
```

## Notes

- SQLite DB path: `jarvis-data/db/jarvis.db`
- Core log file: `logs/jarvis-core.log`
- Agent build/cache path (runtime): `jarvis-data/build/`
- Architecture/planning docs: `discussions/`

If `jarvis-agent` fails to start with `.build` permission errors, use runtime scripts (`./scripts/start_all.sh`) so Swift build output goes to `jarvis-data/build/` instead of `jarvis-agent/.build`.
