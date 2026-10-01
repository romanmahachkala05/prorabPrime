CREATE TABLE contacts (
    id         UUID PRIMARY KEY,
    object_id  UUID         NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    name       VARCHAR(200) NOT NULL,
    phone      VARCHAR(50),
    role       VARCHAR(20)  NOT NULL DEFAULT 'OTHER'
        CHECK (role IN ('CLIENT', 'EXECUTOR', 'OTHER')),
    sort_order INT          NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX contacts_object_id_idx ON contacts (object_id);
