CREATE TABLE objects (
    id             UUID PRIMARY KEY,
    title          VARCHAR(200),
    address        VARCHAR(500) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'DONE', 'PAUSED')),
    client_name    VARCHAR(200),
    client_phone   VARCHAR(50),
    notes          TEXT,
    cover_photo_id UUID,
    -- Reserved for the future custom-fields builder; unused in stage 1.
    custom_fields  JSONB        NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE photos (
    id              UUID PRIMARY KEY,
    object_id       UUID         NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    file_name       VARCHAR(255) NOT NULL,
    thumb_file_name VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    -- After the EXIF orientation is applied.
    width           INT          NOT NULL,
    height          INT          NOT NULL,
    sort_order      INT          NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    -- The target of the composite cover key below.
    UNIQUE (object_id, id)
);

CREATE INDEX photos_object_id_idx ON photos (object_id);

-- Added after both tables exist, since they reference each other. Composite, so a cover can only
-- be a photo of the same object; deleting that photo clears only cover_photo_id (PostgreSQL 15+).
ALTER TABLE objects
    ADD CONSTRAINT objects_cover_photo_fk
        FOREIGN KEY (id, cover_photo_id) REFERENCES photos (object_id, id)
            ON DELETE SET NULL (cover_photo_id);
