-- Recognition points: awarded automatically for attendance and manually by teachers with a short reason.
CREATE TABLE point_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID         NOT NULL,
    site_id         UUID         NOT NULL,
    student_id      UUID         NOT NULL,
    amount          INT          NOT NULL,
    reason          VARCHAR(200) NOT NULL,
    source          VARCHAR(12)  NOT NULL,
    awarded_by      UUID,
    awarded_on      DATE         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_points_student ON point_entries (student_id);
CREATE INDEX idx_points_site ON point_entries (site_id, awarded_on);
-- one automatic attendance point per student per day
CREATE UNIQUE INDEX uq_points_attendance ON point_entries (student_id, awarded_on) WHERE source = 'ATTENDANCE';
