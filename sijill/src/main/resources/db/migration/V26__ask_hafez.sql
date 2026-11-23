-- Ask Hafez: the floating chat helper for teachers, parents and students.
-- A parent can turn it off for a child (the child's button disappears); every exchange is kept in a log with an optional rating.
CREATE TABLE ask_hafez_off (
    student_id     UUID PRIMARY KEY REFERENCES people (id) ON DELETE CASCADE,
    turned_off_by  UUID REFERENCES people (id) ON DELETE SET NULL,
    turned_off_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ask_hafez_log (
    id          UUID PRIMARY KEY,
    org_id      UUID NOT NULL,
    site_id     UUID,
    person_id   UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    question    TEXT NOT NULL,
    answer      TEXT NOT NULL,
    intent      VARCHAR(30) NOT NULL,
    rating      SMALLINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ask_hafez_log_person ON ask_hafez_log (person_id, created_at DESC);
