-- Classroom features for students and parents: daily homework, quizzes that mark themselves, a chat with the
-- teacher, and notices a teacher writes for one class. Also renames the schools and removes stale support tickets.

-- Homework: a teacher sets it for one section and subject, due on a school day.
CREATE TABLE homework (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    section_id UUID NOT NULL REFERENCES sections (id),
    subject_id UUID NOT NULL REFERENCES subjects (id),
    teacher_id UUID NOT NULL REFERENCES people (id),
    title      VARCHAR(200) NOT NULL,
    details    VARCHAR(4000) NOT NULL DEFAULT '',
    due_on     DATE NOT NULL
);
CREATE INDEX ix_homework_section_due ON homework (section_id, due_on);
CREATE INDEX ix_homework_teacher_due ON homework (teacher_id, due_on);

-- Quizzes: multiple-choice questions with the right answer and an explanation; one attempt per student.
CREATE TABLE quizzes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id          UUID NOT NULL,
    site_id         UUID NOT NULL REFERENCES sites (id),
    section_id      UUID NOT NULL REFERENCES sections (id),
    subject_id      UUID NOT NULL REFERENCES subjects (id),
    teacher_id      UUID NOT NULL REFERENCES people (id),
    title           VARCHAR(200) NOT NULL,
    source_material VARCHAR(4000) NOT NULL DEFAULT '',
    source_link     VARCHAR(1000)
);
CREATE INDEX ix_quizzes_section ON quizzes (section_id, created_at DESC);

CREATE TABLE quiz_questions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id       UUID NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    prompt        VARCHAR(1000) NOT NULL,
    options       TEXT NOT NULL,             -- the choices, one per line
    correct_index INTEGER NOT NULL,
    explanation   VARCHAR(2000) NOT NULL DEFAULT ''
);
CREATE INDEX ix_quiz_questions_quiz ON quiz_questions (quiz_id, position);

CREATE TABLE quiz_attempts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id      UUID NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    student_id   UUID NOT NULL REFERENCES people (id),
    answers      VARCHAR(400) NOT NULL,      -- chosen option per question, comma separated (-1 = no answer)
    score        INTEGER NOT NULL,
    total        INTEGER NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (quiz_id, student_id)
);

-- Chat between a teacher and a student or a parent. other_id is always the student or the parent.
CREATE TABLE chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    teacher_id UUID NOT NULL REFERENCES people (id),
    other_id   UUID NOT NULL REFERENCES people (id),
    sender_id  UUID NOT NULL REFERENCES people (id),
    body       VARCHAR(2000) NOT NULL,
    read_at    TIMESTAMPTZ
);
CREATE INDEX ix_chat_pair ON chat_messages (teacher_id, other_id, created_at);
CREATE INDEX ix_chat_other ON chat_messages (other_id, created_at);

-- A notice can belong to one class: a teacher writes it, that class's students and parents see it at the top.
ALTER TABLE notices ADD COLUMN section_id UUID REFERENCES sections (id);

-- Schools are named 1st to 5th Assalam School. School A (the live demo school, where the real accounts are) is the 5th;
-- the others are numbered in the order they were created.
WITH main AS (
    SELECT id FROM sites
    WHERE NOT head_office AND (name = 'School A' OR domain = 'alnoor-academy.edu.qa')
    ORDER BY (name = 'School A') DESC, created_at
    LIMIT 1
), others AS (
    SELECT s.id, row_number() OVER (ORDER BY s.created_at, s.name) AS n
    FROM sites s
    WHERE NOT s.head_office AND s.id NOT IN (SELECT id FROM main)
)
UPDATE sites s SET name = CASE
        WHEN s.id IN (SELECT id FROM main) THEN '5th Assalam School'
        ELSE (SELECT CASE o.n WHEN 1 THEN '1st' WHEN 2 THEN '2nd' WHEN 3 THEN '3rd' WHEN 4 THEN '4th' END
              || ' Assalam School' FROM others o WHERE o.id = s.id)
    END
WHERE s.id IN (SELECT id FROM main)
   OR s.id IN (SELECT id FROM others WHERE n <= 4);

-- Stale support tickets raised by Principal Huda are removed (replies and attachments go with them).
DELETE FROM support_tickets t
USING people p
WHERE p.id = t.created_by AND p.role = 'PRINCIPAL' AND p.display_name ILIKE 'Huda%';
