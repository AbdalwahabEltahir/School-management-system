-- Family engagement: at-risk students, absence notes, trip and event permission, early pick-up requests and
-- parent-teacher meetings. Every table carries org_id and site_id; one school's rows are never shown to another.

-- 1. A student whose average in one subject has fallen below the pass line. Raised and resolved automatically from the
--    submitted marks; staff can note what they are doing about it and tell the parents.
CREATE TABLE risk_flags (
    id              UUID PRIMARY KEY,
    org_id          UUID          NOT NULL,
    site_id         UUID          NOT NULL REFERENCES sites (id),
    student_id      UUID          NOT NULL REFERENCES people (id),
    section_id      UUID          NOT NULL REFERENCES sections (id),
    subject_id      UUID          NOT NULL REFERENCES subjects (id),
    average_pct     NUMERIC(5, 2) NOT NULL,
    lowest_pct      NUMERIC(5, 2) NOT NULL,
    assessments     INTEGER       NOT NULL,
    level           VARCHAR(8)    NOT NULL CHECK (level IN ('WATCH', 'HIGH')),
    status          VARCHAR(10)   NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    raised_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    note            VARCHAR(1000),
    noted_by        UUID REFERENCES people (id),
    noted_at        TIMESTAMPTZ,
    parents_told_at TIMESTAMPTZ,
    parents_told_by UUID REFERENCES people (id)
);
CREATE UNIQUE INDEX uq_risk_open ON risk_flags (student_id, subject_id) WHERE status = 'OPEN';
CREATE INDEX ix_risk_site ON risk_flags (site_id, status);

-- 2. A parent explains an absence (past or planned). The attendance clerk or the principal assistant accepts it, and
--    the absent days become excused leave.
CREATE TABLE absence_excuses (
    id           UUID PRIMARY KEY,
    org_id       UUID          NOT NULL,
    site_id      UUID          NOT NULL REFERENCES sites (id),
    student_id   UUID          NOT NULL REFERENCES people (id),
    parent_id    UUID          NOT NULL REFERENCES people (id),
    from_date    DATE          NOT NULL,
    to_date      DATE          NOT NULL,
    category     VARCHAR(12)   NOT NULL CHECK (category IN ('ILLNESS', 'MEDICAL', 'FAMILY', 'TRAVEL', 'OTHER')),
    details      VARCHAR(1000) NOT NULL DEFAULT '',
    status       VARCHAR(10)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    reviewed_by  UUID REFERENCES people (id),
    reviewed_at  TIMESTAMPTZ,
    review_note  VARCHAR(500),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (to_date >= from_date)
);
CREATE INDEX ix_excuses_site ON absence_excuses (site_id, status, from_date);
CREATE INDEX ix_excuses_student ON absence_excuses (student_id, from_date);

-- The parent is told once per absent day, however often the day's attendance is saved.
CREATE TABLE absence_alerts (
    student_id UUID        NOT NULL REFERENCES people (id),
    absent_on  DATE        NOT NULL,
    sent_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, absent_on)
);

-- 3. A trip or event that needs each parent's permission.
CREATE TABLE school_events (
    id               UUID PRIMARY KEY,
    org_id           UUID          NOT NULL,
    site_id          UUID          NOT NULL REFERENCES sites (id),
    title            VARCHAR(200)  NOT NULL,
    details          VARCHAR(4000) NOT NULL DEFAULT '',
    location         VARCHAR(200)  NOT NULL DEFAULT '',
    starts_at        TIMESTAMPTZ   NOT NULL,
    ends_at          TIMESTAMPTZ   NOT NULL,
    cost             NUMERIC(8, 2),
    consent_deadline DATE          NOT NULL,
    created_by       UUID          NOT NULL REFERENCES people (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cancelled_at     TIMESTAMPTZ,
    cancel_reason    VARCHAR(300),
    CHECK (ends_at >= starts_at)
);
CREATE INDEX ix_events_site ON school_events (site_id, starts_at);

CREATE TABLE school_event_sections (
    event_id   UUID NOT NULL REFERENCES school_events (id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES sections (id),
    PRIMARY KEY (event_id, section_id)
);

-- One answer per child; a parent can change it until the deadline. The statement they agreed to is kept.
CREATE TABLE event_consents (
    event_id   UUID          NOT NULL REFERENCES school_events (id) ON DELETE CASCADE,
    student_id UUID          NOT NULL REFERENCES people (id),
    parent_id  UUID          NOT NULL REFERENCES people (id),
    decision   VARCHAR(8)    NOT NULL CHECK (decision IN ('APPROVED', 'DECLINED')),
    note       VARCHAR(500)  NOT NULL DEFAULT '',
    statement  VARCHAR(800)  NOT NULL DEFAULT '',
    decided_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, student_id)
);

-- 4. A parent asks, in advance, to collect a child before the end of the school day.
CREATE TABLE early_pickup_requests (
    id                     UUID PRIMARY KEY,
    org_id                 UUID         NOT NULL,
    site_id                UUID         NOT NULL REFERENCES sites (id),
    student_id             UUID         NOT NULL REFERENCES people (id),
    section_id             UUID         NOT NULL REFERENCES sections (id),
    parent_id              UUID         NOT NULL REFERENCES people (id),
    pickup_on              DATE         NOT NULL,
    pickup_time            TIME         NOT NULL,
    collector_id           UUID,
    collector_name         VARCHAR(200) NOT NULL,
    collector_relationship VARCHAR(12)  NOT NULL,
    reason                 VARCHAR(300) NOT NULL,
    status                 VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'CANCELLED')),
    decided_by             UUID REFERENCES people (id),
    decided_at             TIMESTAMPTZ,
    decline_reason         VARCHAR(300),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_early_pickup_site ON early_pickup_requests (site_id, pickup_on, status);
-- one live request per child per day
CREATE UNIQUE INDEX uq_early_pickup_live ON early_pickup_requests (student_id, pickup_on) WHERE status IN ('PENDING', 'APPROVED');

-- 5. A teacher's free meeting slots, and parents' bookings of them.
CREATE TABLE meeting_slots (
    id           UUID PRIMARY KEY,
    org_id       UUID         NOT NULL,
    site_id      UUID         NOT NULL REFERENCES sites (id),
    teacher_id   UUID         NOT NULL REFERENCES people (id),
    starts_at    TIMESTAMPTZ  NOT NULL,
    minutes      INTEGER      NOT NULL CHECK (minutes BETWEEN 5 AND 120),
    mode         VARCHAR(10)  NOT NULL CHECK (mode IN ('IN_PERSON', 'ONLINE', 'PHONE')),
    place        VARCHAR(300) NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    removed_at   TIMESTAMPTZ
);
CREATE INDEX ix_slots_teacher ON meeting_slots (teacher_id, starts_at);
CREATE UNIQUE INDEX uq_slots_teacher_time ON meeting_slots (teacher_id, starts_at) WHERE removed_at IS NULL;

CREATE TABLE meeting_bookings (
    id            UUID PRIMARY KEY,
    org_id        UUID          NOT NULL,
    site_id       UUID          NOT NULL REFERENCES sites (id),
    slot_id       UUID          NOT NULL REFERENCES meeting_slots (id),
    parent_id     UUID          NOT NULL REFERENCES people (id),
    student_id    UUID          NOT NULL REFERENCES people (id),
    topic         VARCHAR(500)  NOT NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED')),
    cancelled_by  UUID REFERENCES people (id),
    cancel_reason VARCHAR(300),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cancelled_at  TIMESTAMPTZ
);
-- a slot can be booked by one family at a time: a second booking of the same slot fails here
CREATE UNIQUE INDEX uq_booking_slot ON meeting_bookings (slot_id) WHERE status = 'BOOKED';
CREATE INDEX ix_bookings_parent ON meeting_bookings (parent_id, status);
