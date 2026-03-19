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

CREATE TABLE IF NOT EXISTS jbalance_shops (
    uuid       TEXT    NOT NULL PRIMARY KEY,
    x          REAL    NOT NULL,
    y          REAL    NOT NULL,
    z          REAL    NOT NULL,
    yaw        REAL    NOT NULL DEFAULT 0,
    pitch      REAL    NOT NULL DEFAULT 0,
    dimension  TEXT    NOT NULL DEFAULT 'minecraft:overworld',
    created_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS jbalance_shop_items (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_uuid     TEXT    NOT NULL,
    display_x     INTEGER NOT NULL,
    display_y     INTEGER NOT NULL,
    display_z     INTEGER NOT NULL,
    storage_x     INTEGER NOT NULL,
    storage_y     INTEGER NOT NULL,
    storage_z     INTEGER NOT NULL,
    item_id       TEXT    NOT NULL,
    item_nbt      TEXT    NOT NULL DEFAULT '',
    sell_qty      INTEGER NOT NULL DEFAULT 0,
    sell_price    INTEGER NOT NULL DEFAULT 0,
    buy_qty       INTEGER NOT NULL DEFAULT 0,
    buy_price     INTEGER NOT NULL DEFAULT 0,
    active        INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (shop_uuid) REFERENCES jbalance_shops(uuid)
);
