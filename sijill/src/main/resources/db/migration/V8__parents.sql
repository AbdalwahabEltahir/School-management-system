-- A parent or guardian is linked to the students they may follow. School IT creates the links.
CREATE TABLE parent_links (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id   UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    student_id  UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (parent_id, student_id)
);
CREATE INDEX idx_parent_links_parent ON parent_links (parent_id);
