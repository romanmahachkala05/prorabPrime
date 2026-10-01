-- Which folder of the object a file is in: its photos, or its receipts.
ALTER TABLE photos
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'PHOTO'
        CHECK (kind IN ('PHOTO', 'RECEIPT'));
