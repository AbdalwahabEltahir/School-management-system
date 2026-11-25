-- V27 added the subject columns as SMALLINT; the application reads them as ordinary integers.
ALTER TABLE subjects ALTER COLUMN grade_from TYPE INTEGER;
ALTER TABLE subjects ALTER COLUMN grade_to TYPE INTEGER;
ALTER TABLE subjects ALTER COLUMN max_periods TYPE INTEGER;
