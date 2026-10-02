-- Things to do on a day, with an optional time the phone reminds at.
CREATE TABLE tasks (
    id                UUID PRIMARY KEY,
    title             VARCHAR(300) NOT NULL,
    day               DATE         NOT NULL,
    remind_at_minutes INT CHECK (remind_at_minutes BETWEEN 0 AND 1439),
    done              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX tasks_day_idx ON tasks (day);
