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
