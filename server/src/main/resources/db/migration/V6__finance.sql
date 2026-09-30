-- What was agreed for the whole object, per side, in kopecks. A row exists once either is set.
CREATE TABLE finance_terms (
    object_id           UUID PRIMARY KEY REFERENCES objects (id) ON DELETE CASCADE,
    client_total_kopecks BIGINT CHECK (client_total_kopecks >= 0),
    crew_total_kopecks   BIGINT CHECK (crew_total_kopecks >= 0)
);

CREATE TABLE payments (
    id            UUID PRIMARY KEY,
    object_id     UUID         NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    side          VARCHAR(10)  NOT NULL CHECK (side IN ('CLIENT', 'CREW')),
    amount_kopecks BIGINT      NOT NULL CHECK (amount_kopecks > 0),
    method        VARCHAR(10)  NOT NULL CHECK (method IN ('CASH', 'TRANSFER', 'CARD', 'OTHER')),
    paid_on       DATE         NOT NULL,
    note          VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payments_object_id_idx ON payments (object_id);

-- Every change to a payment, including its deletion. No foreign key to payments: the history of
-- a deleted payment is exactly what this table is for. It goes with the object, though.
CREATE TABLE payment_history (
    id             UUID PRIMARY KEY,
    object_id      UUID         NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    payment_id     UUID         NOT NULL,
    action         VARCHAR(10)  NOT NULL CHECK (action IN ('CREATED', 'UPDATED', 'DELETED')),
    side           VARCHAR(10)  NOT NULL,
    amount_kopecks BIGINT       NOT NULL,
    method         VARCHAR(10)  NOT NULL,
    paid_on        DATE         NOT NULL,
    note           VARCHAR(500),
    at             TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payment_history_object_id_idx ON payment_history (object_id);

CREATE TABLE extra_works (
    id             UUID PRIMARY KEY,
    object_id      UUID         NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    title          VARCHAR(200) NOT NULL,
    amount_kopecks BIGINT       NOT NULL CHECK (amount_kopecks >= 0),
    status         VARCHAR(12)  NOT NULL DEFAULT 'NOT_AGREED' CHECK (status IN ('AGREED', 'NOT_AGREED')),
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX extra_works_object_id_idx ON extra_works (object_id);
