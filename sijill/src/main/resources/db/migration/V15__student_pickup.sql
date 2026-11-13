-- Student pick-up and collection: approved collectors, arrivals, and the chain-of-custody log of releases.

-- People a parent has approved to collect one child. Only the last four characters of the ID are kept.
CREATE TABLE pickup_collectors (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID         NOT NULL,
    site_id       UUID         NOT NULL,
    student_id    UUID         NOT NULL,
    name          VARCHAR(100) NOT NULL,
    relationship  VARCHAR(12)  NOT NULL,
    phone         VARCHAR(30),
    id_last4      VARCHAR(4)   NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pickup_collectors_student ON pickup_collectors (student_id, active);

-- One arrival ("I've arrived") from pressing the button to release, decline or cancel. Names are copied in so the
-- record stays readable whatever happens to the people later.
CREATE TABLE pickup_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID         NOT NULL,
    site_id                 UUID         NOT NULL,
    student_id              UUID         NOT NULL,
    student_name            VARCHAR(200) NOT NULL,
    section_id              UUID         NOT NULL,
    section_label           VARCHAR(40)  NOT NULL,
    parent_id               UUID         NOT NULL,
    collector_id            UUID,
    collector_name          VARCHAR(200) NOT NULL,
    collector_relationship  VARCHAR(12)  NOT NULL,
    collector_id_last4      VARCHAR(4),
    collector_phone         VARCHAR(30),
    status                  VARCHAR(10)  NOT NULL DEFAULT 'ARRIVED',
    arrived_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    verified_by             UUID,
    verified_by_name        VARCHAR(200),
    verified_at             TIMESTAMPTZ,
    decided_by              UUID,
    decided_by_name         VARCHAR(200),
    decided_at              TIMESTAMPTZ,
    decline_reason          VARCHAR(300),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pickup_requests_site ON pickup_requests (site_id, status, updated_at DESC);
-- a child can have only one open arrival at a time
CREATE UNIQUE INDEX uq_pickup_open_request ON pickup_requests (student_id) WHERE status IN ('ARRIVED', 'VERIFYING');

-- The chain-of-custody log: one row for every release. Rows can never be changed or deleted.
CREATE TABLE pickup_release_log (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID         NOT NULL,
    site_id                 UUID         NOT NULL,
    request_id              UUID         NOT NULL,
    student_id              UUID         NOT NULL,
    student_name            VARCHAR(200) NOT NULL,
    section_label           VARCHAR(40)  NOT NULL,
    collector_name          VARCHAR(200) NOT NULL,
    collector_relationship  VARCHAR(12)  NOT NULL,
    released_by             UUID         NOT NULL,
    released_by_name        VARCHAR(200) NOT NULL,
    released_at             TIMESTAMPTZ  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pickup_log_site ON pickup_release_log (site_id, released_at DESC);
CREATE INDEX idx_pickup_log_student ON pickup_release_log (student_id, released_at DESC);

CREATE FUNCTION pickup_release_log_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'The pick-up release log cannot be changed or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER pickup_release_log_no_change BEFORE UPDATE OR DELETE ON pickup_release_log
    FOR EACH ROW EXECUTE FUNCTION pickup_release_log_is_append_only();
