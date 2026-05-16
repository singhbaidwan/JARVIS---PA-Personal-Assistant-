# Phase 9 UI + Avatar Plan

## Goal

Build the first full JARVIS product surface: a futuristic but practical control console that integrates the core features already implemented across `jarvis-core`, `jarvis-ai`, and `jarvis-agent`.

## UI Direction

- Full-screen command center, not a marketing landing page.
- Primary visual anchor: an interactive 3D assistant/avatar scene.
- Dense operational panels for scanning system state, issuing chat requests, searching files, and reviewing suggested actions.
- Keep the palette varied but restrained: dark neutral base, cyan/green operational accents, amber/red guardian states, and white data surfaces.
- Every control should map to real system functionality; no fake feature copy.

## Technology

- Convert `jarvis-ui` to a Vite + React + TypeScript app.
- Use componentized TSX for the console panels so feature surfaces remain easy to evolve.
- Use Three.js for the avatar scene with procedural geometry first; downloaded models can be added later from trusted sources when the model pipeline is ready.
- Use CSS variables and responsive grid/layout primitives instead of a large component dependency for this first React pass.
- Follow `vercel:react-best-practices`: one component per file, named exports, accessible native controls, effect cleanup, typed props, and colocated state.

## API Integrations

- `GET /health` on core and AI for service state.
- `POST /assistant/chat` for Phase 8 chat-based control and safe suggested actions.
- `POST /search` for Phase 7 local smart search.
- `POST /guardian/anomaly` for Phase 6 system guardian scans.
- `POST /behavior-learning/predict` for Phase 5 behavior prediction and guarded enqueue decisions.
- `GET /insights/daily` for usage summaries.
- `GET /command` for queued and executed automation commands.
- `GET /workflow` for workflow status.
- `GET /event` for recent telemetry and agent events.

## Interaction Model

1. User configures core and AI base URLs in the top status rail.
2. UI checks health and shows service readiness.
3. Avatar state changes based on mode:
   - idle: calm orbiting core
   - thinking: faster ring motion
   - guardian: amber/red scan pulse
   - success: green pulse
   - error: red pulse
4. Chat panel sends to core `/assistant/chat`, displays assistant text and suggested actions with approval state.
5. Search panel sends to core `/search`, lists ranked local files with snippets.
6. Guardian panel calls `/guardian/anomaly`, highlights severity, score, and signals.
7. Behavior panel calls `/behavior-learning/predict`, showing prediction and enqueue decision.
8. Operations panels refresh commands, workflows, events, and daily insights.

## Implementation Phases

### Phase 9.1: Foundation

- Convert `package.json` scripts to Vite.
- Add `index.html`, `src/main.tsx`, app-level CSS, and typed API service.
- Add accessible buttons, inputs, tabs, and status regions as React components.

### Phase 9.2: 3D Avatar

- Add Three.js scene with procedural geometry.
- Animate rings, particles, and central assistant core.
- Tie animation state to UI mode.

### Phase 9.3: Core Feature Panels

- Chat control with suggestions.
- Smart search.
- Guardian scan.
- Behavior learning.

### Phase 9.4: Operations Dashboard

- Health, insights, commands, workflows, and event telemetry.
- Refresh controls and compact data rendering.

### Phase 9.5: Verification

- Run React build and lint/type checks where configured.
- Run Vite dev server.
- Verify desktop and mobile rendering with Playwright screenshots.
- Verify canvas is nonblank and panels do not overlap.
- Smoke test API-backed flows against running services where possible.
