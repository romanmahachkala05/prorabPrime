-- Lowercased address and title, filled in by the server, for case-insensitive search.
-- Not lower()/ILIKE: those fold case by the database's locale, and under "C" only ASCII.
ALTER TABLE objects ADD COLUMN search_text TEXT NOT NULL DEFAULT '';
