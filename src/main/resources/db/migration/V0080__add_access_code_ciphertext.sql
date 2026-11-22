-- Admin-issued access codes can be shown again to admins: the full code is kept encrypted
-- (AES-256-GCM, key ACCESS_CODE_ENCRYPTION_KEY). Lookups still use code_hash.
-- Codes issued before this migration, and gift codes, stay hash-only and cannot be revealed.
ALTER TABLE access_codes ADD COLUMN code_ciphertext text;
