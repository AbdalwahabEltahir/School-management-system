-- School achievements (each school fills in a template; leadership sees every school's), teacher performance
-- indicators that add up to warnings and investigations, and two more kinds of quiz question.

-- An achievement a school records against EAA's success measures (for example "Opened a VR room").
CREATE TABLE school_achievements (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id        UUID NOT NULL,
    site_id       UUID NOT NULL REFERENCES sites (id),
    category      VARCHAR(20) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    description   VARCHAR(4000) NOT NULL DEFAULT '',
    impact        VARCHAR(1000) NOT NULL DEFAULT '',
    achieved_on   DATE NOT NULL,
    evidence_link VARCHAR(1000),
    points        INTEGER NOT NULL,
    created_by    UUID NOT NULL REFERENCES people (id)
);
CREATE INDEX ix_achievements_site ON school_achievements (site_id, achieved_on DESC);

-- The head teacher records an indicator when a teacher is not delivering as expected. Three indicators make one
-- warning; three warnings open an investigation.
CREATE TABLE teacher_warnings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id           UUID NOT NULL,
    site_id          UUID NOT NULL REFERENCES sites (id),
    teacher_id       UUID NOT NULL REFERENCES people (id),
    number           INTEGER NOT NULL,
    investigation_id UUID
);
CREATE INDEX ix_warnings_teacher ON teacher_warnings (teacher_id, created_at);

CREATE TABLE teacher_indicators (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id      UUID NOT NULL,
    site_id     UUID NOT NULL REFERENCES sites (id),
    teacher_id  UUID NOT NULL REFERENCES people (id),
    recorded_by UUID NOT NULL REFERENCES people (id),
    category    VARCHAR(20) NOT NULL,
    note        VARCHAR(2000) NOT NULL,
    warning_id  UUID REFERENCES teacher_warnings (id)
);
CREATE INDEX ix_indicators_teacher ON teacher_indicators (teacher_id, created_at);

CREATE TABLE investigations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    teacher_id UUID NOT NULL REFERENCES people (id),
    status     VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    closed_at  TIMESTAMPTZ,
    outcome    VARCHAR(4000)
);
ALTER TABLE teacher_warnings ADD CONSTRAINT fk_warning_investigation FOREIGN KEY (investigation_id) REFERENCES investigations (id);

CREATE TABLE investigation_members (
    investigation_id UUID NOT NULL REFERENCES investigations (id) ON DELETE CASCADE,
    person_id        UUID NOT NULL REFERENCES people (id),
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (investigation_id, person_id)
);

CREATE TABLE investigation_notes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    investigation_id UUID NOT NULL REFERENCES investigations (id) ON DELETE CASCADE,
    author_id        UUID NOT NULL REFERENCES people (id),
    body             VARCHAR(4000) NOT NULL
);

-- Quiz questions can be multiple choice (as before), true / false, or a short written answer marked against the
-- accepted answers (one per line, capitals and spaces ignored).
ALTER TABLE quiz_questions ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'CHOICE';
ALTER TABLE quiz_questions ADD COLUMN accepted VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE quiz_attempts ADD COLUMN written TEXT NOT NULL DEFAULT '';
