CREATE TABLE IF NOT EXISTS jbalance_players (
    uuid         TEXT    NOT NULL PRIMARY KEY,
    display_name TEXT    NOT NULL DEFAULT '',
    balance      INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS jbalance_playtime (
    uuid             TEXT    NOT NULL PRIMARY KEY,
    active_seconds   INTEGER NOT NULL DEFAULT 0,
    claimed_hours    TEXT    NOT NULL DEFAULT ''
);
