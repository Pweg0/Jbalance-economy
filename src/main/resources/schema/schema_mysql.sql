CREATE TABLE IF NOT EXISTS jbalance_players (
    uuid         CHAR(36)     NOT NULL PRIMARY KEY,
    display_name VARCHAR(16)  NOT NULL DEFAULT '',
    balance      BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS jbalance_playtime (
    uuid             CHAR(36)   NOT NULL PRIMARY KEY,
    active_seconds   BIGINT     NOT NULL DEFAULT 0,
    claimed_hours    TEXT       NOT NULL DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
