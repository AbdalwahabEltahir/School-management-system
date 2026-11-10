-- The coordinator creates the school's terms (FR-016). Assessments may only be set for a term that exists.
CREATE TABLE terms (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID NOT NULL,
    site_id    UUID NOT NULL REFERENCES sites (id),
    number     INTEGER NOT NULL CHECK (number BETWEEN 1 AND 3),
    name       VARCHAR(80) NOT NULL,
    starts_on  DATE NOT NULL,
    ends_on    DATE NOT NULL,
    UNIQUE (site_id, number),
    CHECK (ends_on > starts_on)
);
