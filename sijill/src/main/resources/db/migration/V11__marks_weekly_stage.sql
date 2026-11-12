-- Weekly grade submissions and the Sijil -> Hifz progression.
ALTER TABLE assessments ADD COLUMN week_start DATE;
UPDATE assessments SET week_start = (created_at::date - EXTRACT(DOW FROM created_at)::int);
ALTER TABLE assessments ALTER COLUMN week_start SET NOT NULL;

ALTER TABLE enrolments ADD COLUMN stage VARCHAR(10) NOT NULL DEFAULT 'SIJIL';
