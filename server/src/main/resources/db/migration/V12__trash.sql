-- A deleted object or photo is not removed at once: it waits in the trash, marked here, until it is
-- restored or the retention period is over. Every ordinary query leaves out a row that is marked.
ALTER TABLE objects ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE photos ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX objects_deleted_at_idx ON objects (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX photos_deleted_at_idx ON photos (deleted_at) WHERE deleted_at IS NOT NULL;
