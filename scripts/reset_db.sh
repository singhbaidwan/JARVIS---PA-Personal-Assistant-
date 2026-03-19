#!/usr/bin/env bash
set -euo pipefail
DB_PATH="$(dirname "$0")/../jarvis-data/db/jarvis.db"
rm -f "$DB_PATH"
echo "Reset DB: $DB_PATH"
