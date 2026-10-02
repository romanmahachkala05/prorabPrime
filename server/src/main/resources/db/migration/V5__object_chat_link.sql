-- A link to the object's chat in a messenger; the app opens it as is.
ALTER TABLE objects ADD COLUMN chat_link VARCHAR(500);
