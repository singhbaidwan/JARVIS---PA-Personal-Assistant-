#!/usr/bin/env bash
set -euo pipefail
tail -f "$(dirname "$0")/../logs/jarvis-core.log" "$(dirname "$0")/../logs/jarvis-ai.log" "$(dirname "$0")/../logs/jarvis-agent.log"
