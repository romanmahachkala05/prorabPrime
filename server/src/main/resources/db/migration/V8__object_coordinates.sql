-- Where the object is, for the map; filled in by the server from the address, and null when unknown.
ALTER TABLE objects
    ADD COLUMN latitude  DOUBLE PRECISION CHECK (latitude BETWEEN -90 AND 90),
    ADD COLUMN longitude DOUBLE PRECISION CHECK (longitude BETWEEN -180 AND 180);
