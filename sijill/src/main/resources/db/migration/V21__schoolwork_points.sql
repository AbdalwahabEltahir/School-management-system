-- Points now come from schoolwork: homework a student marks as done, quizzes they take, and their published exam
-- and assessment marks. Attendance no longer earns points: earlier attendance points stay in the table for the record
-- but are no longer counted.

-- What a point entry was earned for (the homework, quiz or assessment), so each one is counted once.
ALTER TABLE point_entries ADD COLUMN ref_id UUID;
CREATE UNIQUE INDEX uq_points_ref ON point_entries (student_id, source, ref_id) WHERE ref_id IS NOT NULL;

-- Homework a student has marked as done.
CREATE TABLE homework_done (
    homework_id UUID NOT NULL REFERENCES homework (id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES people (id),
    done_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (homework_id, student_id)
);

