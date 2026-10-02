-- Accounts: each person has their own data on one server (ADR-0021). A token belongs to one user and is
-- stored only as its SHA-256, so a read of the database does not hand out a way in.
CREATE TABLE users (
    id         UUID PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE api_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    -- ENV: the API_TOKEN of the server's environment, kept in step with it at every start.
    -- ISSUED: made by the server's own command and removed only by it.
    source     VARCHAR(10) NOT NULL CHECK (source IN ('ENV', 'ISSUED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX api_tokens_user_id_idx ON api_tokens (user_id);

-- The first account takes everything that existed before there were accounts (V14).
INSERT INTO users (id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'owner', now());
