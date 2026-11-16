-- Points for schoolwork done before points came from schoolwork (V21): every result already published and every
-- quiz already taken earns its points once. Safe to run on any database: entries that exist are left alone.

-- Results already published earn their exam points now (up to 10 each, one for every 10% of the mark).
INSERT INTO point_entries (org_id, site_id, student_id, amount, reason, source, awarded_on, ref_id)
SELECT m.org_id, m.site_id, m.student_id, LEAST(10, GREATEST(0, round(10 * m.mark_value / a.max_mark)))::int,
       left(a.name || ' (' || round(100 * m.mark_value / a.max_mark) || '%)', 200), 'EXAM', COALESCE(a.published_at, a.submitted_at)::date, a.id
FROM marks m JOIN assessments a ON a.id = m.assessment_id
WHERE a.submitted_at IS NOT NULL AND NOT m.absent AND m.mark_value IS NOT NULL
  AND (a.published_at IS NOT NULL OR EXISTS (SELECT 1 FROM result_publications rp WHERE rp.section_id = a.section_id AND rp.term = a.term))
ON CONFLICT (student_id, source, ref_id) WHERE ref_id IS NOT NULL DO NOTHING;

-- Quizzes already taken earn their points now: 2 for taking it, plus up to 8 for the right answers.
INSERT INTO point_entries (org_id, site_id, student_id, amount, reason, source, awarded_on, ref_id)
SELECT q.org_id, q.site_id, qa.student_id, 2 + CASE WHEN qa.total = 0 THEN 0 ELSE round(8.0 * qa.score / qa.total)::int END,
       left('Quiz: ' || q.title || ' (' || qa.score || '/' || qa.total || ')', 200), 'QUIZ', qa.submitted_at::date, q.id
FROM quiz_attempts qa JOIN quizzes q ON q.id = qa.quiz_id
ON CONFLICT (student_id, source, ref_id) WHERE ref_id IS NOT NULL DO NOTHING;
