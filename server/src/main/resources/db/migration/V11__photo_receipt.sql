-- What the fiscal QR code on a receipt says: the sum, when it was bought, and the code's own text
-- (kept so the receipt can be looked up later). All null for a photo and for a receipt with no readable code.
ALTER TABLE photos
    ADD COLUMN receipt_amount_kopecks BIGINT,
    ADD COLUMN receipt_at VARCHAR(20),
    ADD COLUMN receipt_qr TEXT;
