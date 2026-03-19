# JARVIS - Personal Assistant (Phase 0 + Phase 1)

This repository now includes the Phase 0 monorepo setup and Phase 1 event pipeline foundation.

## Monorepo Layout

- `jarvis-core` - Kotlin + Spring Boot core service (event API + SQLite)
- `jarvis-agent` - Swift macOS app monitor (posts app-switch events)
- `jarvis-ai` - Python FastAPI AI service scaffold
- `jarvis-runtime` - Process manager scripts
- `jarvis-ui` - UI placeholder scaffold
- `jarvis-data` - local DB/migrations/vector storage directories
- `logs` - service logs

## Phase 1 Event Pipeline

### 1. Start `jarvis-core`

`jarvis-core` currently expects Gradle wrapper (`./gradlew`). If wrapper is not yet added, install Gradle and generate it:

```bash
cd jarvis-core
gradle wrapper
./gradlew bootRun
```

Core API:
- `POST /event`
- `GET /event`
- `GET /health`

### 2. Start `jarvis-agent`

```bash
cd jarvis-agent
swift run
```

Optional endpoint override:

```bash
export JARVIS_CORE_EVENT_URL=http://127.0.0.1:8080/event
```

### 3. Test End-to-End Quickly

Manual post:

```bash
curl -X POST http://127.0.0.1:8080/event \
  -H "Content-Type: application/json" \
  -d '{"type":"APP_OPENED","payload":{"app":"Chrome"},"source":"manual-test"}'
```

Then check recent events:

```bash
curl http://127.0.0.1:8080/event
```

## Runtime Scripts

- `scripts/start_all.sh`
- `scripts/stop_all.sh`
- `scripts/status.sh`
- `scripts/reset_db.sh`
- `scripts/tail_logs.sh`

## Notes

- SQLite DB path: `jarvis-data/db/jarvis.db`
- Core log file: `logs/jarvis-core.log`
- Architecture and planning docs remain in `discussions/`.
