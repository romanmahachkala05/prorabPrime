-- The checklist of materials of an object: each is not chosen, chosen, or already in the apartment.
CREATE TABLE materials (
    id         UUID PRIMARY KEY,
    object_id  UUID         NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    status     VARCHAR(12)  NOT NULL DEFAULT 'NOT_CHOSEN' CHECK (status IN ('NOT_CHOSEN', 'CHOSEN', 'IN_APARTMENT')),
    sort_order INT          NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX materials_object_id_idx ON materials (object_id);
