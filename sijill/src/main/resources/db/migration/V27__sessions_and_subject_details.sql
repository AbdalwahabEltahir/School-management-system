-- A term belongs to a school session (2025-2026, 2026-2027): the same term number can exist once in each session.
ALTER TABLE terms ADD COLUMN session VARCHAR(9);
UPDATE terms SET session = CASE
    WHEN EXTRACT(MONTH FROM starts_on) >= 8 THEN CAST(EXTRACT(YEAR FROM starts_on) AS INTEGER) || '-' || (CAST(EXTRACT(YEAR FROM starts_on) AS INTEGER) + 1)
    ELSE (CAST(EXTRACT(YEAR FROM starts_on) AS INTEGER) - 1) || '-' || CAST(EXTRACT(YEAR FROM starts_on) AS INTEGER) END;
ALTER TABLE terms ALTER COLUMN session SET NOT NULL;
ALTER TABLE terms DROP CONSTRAINT terms_site_id_number_key;
ALTER TABLE terms ADD CONSTRAINT terms_site_session_number_key UNIQUE (site_id, session, number);

-- A subject has a short code, the grades it is taught in, and how many periods a week it should have.
ALTER TABLE subjects ADD COLUMN code VARCHAR(12);
ALTER TABLE subjects ADD COLUMN grade_from SMALLINT NOT NULL DEFAULT 1 CHECK (grade_from BETWEEN 1 AND 12);
ALTER TABLE subjects ADD COLUMN grade_to SMALLINT NOT NULL DEFAULT 12 CHECK (grade_to BETWEEN 1 AND 12);
ALTER TABLE subjects ADD COLUMN max_periods SMALLINT NOT NULL DEFAULT 5 CHECK (max_periods BETWEEN 1 AND 40);
