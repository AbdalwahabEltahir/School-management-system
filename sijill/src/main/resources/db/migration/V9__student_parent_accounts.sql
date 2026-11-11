-- Students and parents can register with an e-mail and a password of their own (staff keep signing in with Google).
-- A student has a student ID that a parent uses to register and follow them; a class request is how a student or
-- parent asks for a place in a class, which the coordinator then confirms.
ALTER TABLE people ADD COLUMN student_code VARCHAR(20);
UPDATE people SET student_code = 'STU-' || upper(substr(md5(random()::text || id::text), 1, 8)) WHERE role = 'STUDENT';
CREATE UNIQUE INDEX uq_people_student_code ON people (student_code) WHERE student_code IS NOT NULL;

CREATE TABLE credentials (
    person_id       UUID PRIMARY KEY REFERENCES people (id) ON DELETE CASCADE,
    password_hash   VARCHAR(100) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE enrolment_requests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id      UUID NOT NULL,
    student_id   UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    section_id   UUID NOT NULL REFERENCES sections (id) ON DELETE CASCADE,
    requested_by UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    status       VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by   UUID,
    decided_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_enrolment_request_pending ON enrolment_requests (student_id) WHERE status = 'PENDING';
CREATE INDEX idx_enrolment_requests_site ON enrolment_requests (site_id, status);
