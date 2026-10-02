-- Whose objects and tasks these are (ADR-0021). Everything else hangs on an object and is reached through it.
-- What exists now belongs to the first account, the one V13 made.
ALTER TABLE objects ADD COLUMN owner_id UUID REFERENCES users (id);
ALTER TABLE tasks ADD COLUMN owner_id UUID REFERENCES users (id);

UPDATE objects SET owner_id = (SELECT id FROM users ORDER BY created_at, id LIMIT 1);
UPDATE tasks SET owner_id = (SELECT id FROM users ORDER BY created_at, id LIMIT 1);

ALTER TABLE objects ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE tasks ALTER COLUMN owner_id SET NOT NULL;

CREATE INDEX objects_owner_id_idx ON objects (owner_id);
CREATE INDEX tasks_owner_day_idx ON tasks (owner_id, day);
