-- Phase 2: the school portal for teachers and students. Every table carries org_id and site_id (one school's
-- data is never visible to another school). School days are Sunday (0) to Thursday (4).

CREATE TABLE sections (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    grade      INTEGER NOT NULL CHECK (grade BETWEEN 1 AND 12),
    name       VARCHAR(40) NOT NULL,
    UNIQUE (site_id, grade, name)
);

-- One section per student (BR-002).
CREATE TABLE enrolments (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    section_id UUID NOT NULL REFERENCES sections (id),
    student_id UUID NOT NULL REFERENCES people (id),
    UNIQUE (student_id)
);

CREATE TABLE teaching_assignments (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    section_id UUID NOT NULL REFERENCES sections (id),
    subject_id UUID NOT NULL REFERENCES subjects (id),
    teacher_id UUID NOT NULL REFERENCES people (id),
    UNIQUE (section_id, subject_id)
);

-- A section, a teacher and a room can each be in only one entry per period (BR-003).
CREATE TABLE timetable_entries (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    org_id      UUID NOT NULL,
    site_id     UUID NOT NULL REFERENCES sites (id),
    section_id  UUID NOT NULL REFERENCES sections (id),
    subject_id  UUID NOT NULL REFERENCES subjects (id),
    teacher_id  UUID NOT NULL REFERENCES people (id),
    day_of_week INTEGER NOT NULL CHECK (day_of_week BETWEEN 0 AND 4),
    period      INTEGER NOT NULL CHECK (period BETWEEN 1 AND 12),
    room        VARCHAR(40),
    UNIQUE (section_id, day_of_week, period),
    UNIQUE (teacher_id, day_of_week, period)
);
CREATE UNIQUE INDEX ux_timetable_room ON timetable_entries (site_id, lower(room), day_of_week, period) WHERE room IS NOT NULL;

CREATE TABLE attendance_records (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL,
    org_id          UUID NOT NULL,
    site_id         UUID NOT NULL REFERENCES sites (id),
    section_id      UUID NOT NULL REFERENCES sections (id),
    student_id      UUID NOT NULL REFERENCES people (id),
    attendance_date DATE NOT NULL,
    status          VARCHAR(10) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LEAVE')),
    recorded_by     UUID NOT NULL REFERENCES people (id),
    reason          VARCHAR(500),
    UNIQUE (student_id, attendance_date)
);
CREATE INDEX ix_attendance_section_date ON attendance_records (section_id, attendance_date);

CREATE TABLE assessments (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    org_id       UUID NOT NULL,
    site_id      UUID NOT NULL REFERENCES sites (id),
    section_id   UUID NOT NULL REFERENCES sections (id),
    subject_id   UUID NOT NULL REFERENCES subjects (id),
    term         INTEGER NOT NULL CHECK (term BETWEEN 1 AND 3),
    name         VARCHAR(120) NOT NULL,
    max_mark     NUMERIC(6, 2) NOT NULL CHECK (max_mark > 0),
    submitted_at TIMESTAMPTZ
);

CREATE TABLE marks (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    org_id        UUID NOT NULL,
    site_id       UUID NOT NULL REFERENCES sites (id),
    assessment_id UUID NOT NULL REFERENCES assessments (id),
    student_id    UUID NOT NULL REFERENCES people (id),
    mark_value    NUMERIC(6, 2),
    absent        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (assessment_id, student_id)
);

-- Students see a term's results only after the Admin publishes them (BR-007).
CREATE TABLE result_publications (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    org_id       UUID NOT NULL,
    site_id      UUID NOT NULL REFERENCES sites (id),
    section_id   UUID NOT NULL REFERENCES sections (id),
    term         INTEGER NOT NULL CHECK (term BETWEEN 1 AND 3),
    published_at TIMESTAMPTZ NOT NULL,
    published_by UUID NOT NULL REFERENCES people (id),
    UNIQUE (section_id, term)
);

CREATE TABLE notices (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    title_en   VARCHAR(200) NOT NULL,
    title_ar   VARCHAR(200) NOT NULL,
    body_en    VARCHAR(4000) NOT NULL,
    body_ar    VARCHAR(4000) NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    audience   VARCHAR(10) NOT NULL CHECK (audience IN ('ALL', 'STAFF', 'STUDENTS')),
    created_by UUID NOT NULL REFERENCES people (id),
    CHECK (ends_at > starts_at)
);

CREATE TABLE study_materials (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    section_id UUID NOT NULL REFERENCES sections (id),
    subject_id UUID NOT NULL REFERENCES subjects (id),
    title      VARCHAR(200) NOT NULL,
    link       VARCHAR(1000) NOT NULL CHECK (link LIKE 'https://%'),
    created_by UUID NOT NULL REFERENCES people (id)
);
CREATE INDEX ix_materials_section ON study_materials (section_id);
