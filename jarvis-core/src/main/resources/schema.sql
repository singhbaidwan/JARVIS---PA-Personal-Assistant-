CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    source TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at DESC);

CREATE TABLE IF NOT EXISTS daily_app_usage (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    day TEXT NOT NULL,
    app_name TEXT NOT NULL,
    duration_seconds INTEGER NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(day, app_name)
);

CREATE INDEX IF NOT EXISTS idx_daily_app_usage_day ON daily_app_usage(day DESC);

CREATE TABLE IF NOT EXISTS automation_commands (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    action TEXT NOT NULL,
    params TEXT NOT NULL,
    source TEXT,
    status TEXT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 5,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    scheduled_at TEXT NOT NULL,
    claimed_by TEXT,
    claimed_at TEXT,
    last_error TEXT,
    rollback_action TEXT,
    rollback_params TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_automation_commands_queue
    ON automation_commands(status, priority, scheduled_at, created_at);

CREATE INDEX IF NOT EXISTS idx_automation_commands_created_at
    ON automation_commands(created_at DESC);
