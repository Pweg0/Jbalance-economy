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

CREATE TABLE IF NOT EXISTS jbalance_shops (
    uuid       CHAR(36)    NOT NULL PRIMARY KEY,
    x          DOUBLE      NOT NULL,
    y          DOUBLE      NOT NULL,
    z          DOUBLE      NOT NULL,
    yaw        FLOAT       NOT NULL DEFAULT 0,
    pitch      FLOAT       NOT NULL DEFAULT 0,
    dimension  VARCHAR(64) NOT NULL DEFAULT 'minecraft:overworld',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS jbalance_shop_items (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    shop_uuid     CHAR(36)    NOT NULL,
    display_x     INT         NOT NULL,
    display_y     INT         NOT NULL,
    display_z     INT         NOT NULL,
    storage_x     INT         NOT NULL,
    storage_y     INT         NOT NULL,
    storage_z     INT         NOT NULL,
    item_id       VARCHAR(128) NOT NULL,
    item_nbt      TEXT        NOT NULL,
    sell_qty      INT         NOT NULL DEFAULT 0,
    sell_price    BIGINT      NOT NULL DEFAULT 0,
    buy_qty       INT         NOT NULL DEFAULT 0,
    buy_price     BIGINT      NOT NULL DEFAULT 0,
    active        TINYINT     NOT NULL DEFAULT 1,
    FOREIGN KEY (shop_uuid) REFERENCES jbalance_shops(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
