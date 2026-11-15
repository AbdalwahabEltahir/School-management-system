-- A teacher can publish one assessment's marks (for example the exam) without waiting for the rest of the term.
-- Publishing a whole term (result_publications) still works as before; this only adds the single-assessment way.
ALTER TABLE assessments ADD COLUMN published_at TIMESTAMPTZ;
