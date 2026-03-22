# JARVIS - Personal Assistant (Phase 0 + Phase 1 + Phase 2 + Phase 3)

This repository now includes:
- Phase 0 monorepo setup
- Phase 1 event pipeline foundation
- Phase 2 daily insights engine (`/insights/daily` + CLI output)
- Phase 3 command execution layer (`/command` queue + agent execution)

## Monorepo Layout

- `jarvis-core` - Kotlin + Spring Boot core service (events, insights, command queue)
- `jarvis-agent` - Swift macOS agent (app monitoring + command execution)
- `jarvis-ai` - Python FastAPI AI service scaffold
- `jarvis-runtime` - Process manager scripts
- `jarvis-ui` - UI placeholder scaffold
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
- `GET /health`

## Agent Configuration

Preferred base URL:

```bash
export JARVIS_CORE_BASE_URL=http://127.0.0.1:8080
```

Optional:

```bash
export JARVIS_AGENT_ID=jarvis-agent
export JARVIS_COMMAND_POLL_INTERVAL_SECONDS=3
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

4. Check command state transitions:

```bash
curl http://127.0.0.1:8080/command
```

Expected lifecycle: `QUEUED` -> `IN_PROGRESS` -> `SUCCEEDED` (or retry/fail).

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

6. Stop services:

```bash
./scripts/stop_all.sh
```

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
- Architecture/planning docs: `discussions/`
