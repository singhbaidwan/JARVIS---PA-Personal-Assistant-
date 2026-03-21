# JARVIS - Personal Assistant (Phase 0 + Phase 1 + Phase 2)

This repository now includes:
- Phase 0 monorepo setup
- Phase 1 event pipeline foundation
- Phase 2 daily insights engine (`/insights/daily` + CLI output)

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
- `GET /insights/daily`
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

### 4. Get Daily Insights

API:

```bash
curl http://127.0.0.1:8080/insights/daily
```

Optional date:

```bash
curl "http://127.0.0.1:8080/insights/daily?date=2026-03-21"
```

CLI output:

```bash
./scripts/insights.sh
./scripts/insights.sh --date 2026-03-21
```

## Runtime Scripts

- `scripts/start_all.sh`
- `scripts/stop_all.sh`
- `scripts/status.sh`
- `scripts/insights.sh`
- `scripts/reset_db.sh`
- `scripts/tail_logs.sh`

## Run and Verify Phase 2

From repo root:

1. Start all services:

```bash
./scripts/start_all.sh
```

2. Confirm services are running:

```bash
./scripts/status.sh
```

`jarvis-core` should be healthy on `http://127.0.0.1:8080/health`.

3. Send sample events:

```bash
curl -X POST http://127.0.0.1:8080/event \
  -H "Content-Type: application/json" \
  -d '{"type":"APP_OPENED","payload":{"app":"Chrome"},"source":"manual-test"}'

curl -X POST http://127.0.0.1:8080/event \
  -H "Content-Type: application/json" \
  -d '{"type":"APP_SWITCHED","payload":{"from":"Chrome","to":"VS Code"},"source":"manual-test"}'
```

4. Verify raw events:

```bash
curl http://127.0.0.1:8080/event
```

5. Verify daily insights API:

```bash
curl http://127.0.0.1:8080/insights/daily
```

Optional date:

```bash
curl "http://127.0.0.1:8080/insights/daily?date=2026-03-21"
```

6. Verify CLI insights output:

```bash
./scripts/insights.sh
./scripts/insights.sh --date 2026-03-21
```

7. Run tests:

```bash
cd jarvis-core
./gradlew test
cd ..
```

8. Stop services:

```bash
./scripts/stop_all.sh
```

Expected result:
- `/insights/daily` returns `apps`, `totalTracked`, `totalTrackedSeconds`.
- `./scripts/insights.sh` prints per-app durations and a total.

## Notes

- SQLite DB path: `jarvis-data/db/jarvis.db`
- Core log file: `logs/jarvis-core.log`
- Architecture and planning docs remain in `discussions/`.
