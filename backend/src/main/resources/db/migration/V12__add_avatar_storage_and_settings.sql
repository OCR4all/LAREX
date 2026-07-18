CREATE TABLE user_avatars
(
    user_id       VARCHAR(255) PRIMARY KEY,
    storage_key   VARCHAR(255) NOT NULL UNIQUE,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE avatar_runtime_settings
(
    id                    SMALLINT PRIMARY KEY CHECK (id = 1),
    default_style         VARCHAR(32) NOT NULL CHECK (
        default_style IN ('GRADIENT', 'IDENTICON', 'FLOW_FIELD', 'INITIALS')
    ),
    updated_at            TIMESTAMP WITHOUT TIME ZONE,
    updated_by_user_id    VARCHAR(255)
);

INSERT INTO avatar_runtime_settings (id, default_style)
VALUES (1, 'GRADIENT');
