-- =====================================================================================
-- V2: Phase 1 schema for Hafez Files (proposal section 7, "Data model").
--
-- Conventions
--   * Every school-scoped table carries id, created_at, org_id and site_id (see BaseEntity).
--     orgs has no org_id; sites and rule tables are org-scoped only (OrgScopedEntity).
--   * Enums are stored as VARCHAR (EnumType.STRING). Hibernate runs with ddl-auto=validate,
--     so every column here must match its entity in qa.hafez.hafez.model.
--   * No file content is ever stored: only metadata, sharing, masked evidence, issues, messages, logs.
-- =====================================================================================

-- ---------- configuration ----------------------------------------------------------

CREATE TABLE orgs (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    name       VARCHAR(200) NOT NULL
);

CREATE TABLE sites (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMPTZ  NOT NULL,
    org_id         UUID         NOT NULL REFERENCES orgs (id),
    name           VARCHAR(200) NOT NULL,
    domain         VARCHAR(255) NOT NULL UNIQUE,
    head_office    BOOLEAN      NOT NULL DEFAULT FALSE,
    scored         BOOLEAN      NOT NULL DEFAULT TRUE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    checks_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    principal_id   UUID,
    coordinator_id UUID,
    first_check_at TIMESTAMPTZ
);
CREATE INDEX ix_sites_org ON sites (org_id);

CREATE TABLE subjects (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMPTZ  NOT NULL,
    org_id         UUID         NOT NULL,
    site_id        UUID         NOT NULL REFERENCES sites (id),
    name           VARCHAR(150) NOT NULL,
    coordinator_id UUID
);
CREATE INDEX ix_subjects_site ON subjects (site_id);

CREATE TABLE doc_types (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL,
    org_id              UUID         NOT NULL REFERENCES orgs (id),
    name                VARCHAR(100) NOT NULL,
    default_sensitivity VARCHAR(20)  NOT NULL,
    trusted             BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (org_id, name)
);

CREATE TABLE role_groups (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ  NOT NULL,
    org_id          UUID         NOT NULL REFERENCES orgs (id),
    directory_group VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    UNIQUE (org_id, directory_group)
);

CREATE TABLE settings (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ   NOT NULL,
    org_id        UUID          NOT NULL,
    site_id       UUID          NOT NULL REFERENCES sites (id),
    setting_key   VARCHAR(100)  NOT NULL,
    setting_value VARCHAR(1000) NOT NULL,
    UNIQUE (site_id, setting_key)
);

-- ---------- people -----------------------------------------------------------------

CREATE TABLE people (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMPTZ  NOT NULL,
    org_id          UUID         NOT NULL,
    site_id         UUID         NOT NULL REFERENCES sites (id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(200) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    subject_id      UUID REFERENCES subjects (id),
    generic         BOOLEAN      NOT NULL DEFAULT FALSE,
    overridden      BOOLEAN      NOT NULL DEFAULT FALSE,
    directory_role  VARCHAR(20),
    directory_status VARCHAR(20),
    welcome_sent_at TIMESTAMPTZ,
    -- "My preferences" (FR-11.2) live on the person: same lifecycle, one row less to join.
    language        VARCHAR(10)  NOT NULL DEFAULT 'AR',
    digest_mode     VARCHAR(20)  NOT NULL DEFAULT 'PER_ISSUE'
);
CREATE INDEX ix_people_site_role ON people (site_id, role);

-- ---------- files (extends V1) -----------------------------------------------------

ALTER TABLE files
    ADD COLUMN drive_type      VARCHAR(20) NOT NULL DEFAULT 'SHARED',
    ADD COLUMN folder_path     VARCHAR(1000),
    ADD COLUMN mime_type       VARCHAR(255),
    ADD COLUMN checksum        VARCHAR(128),
    ADD COLUMN subject_id      UUID,
    ADD COLUMN exam_date       DATE,
    ADD COLUMN modified_at     TIMESTAMPTZ,
    ADD COLUMN trashed_at      TIMESTAMPTZ,
    ADD COLUMN last_checked_at TIMESTAMPTZ;
ALTER TABLE files ADD CONSTRAINT fk_files_site FOREIGN KEY (site_id) REFERENCES sites (id);
CREATE INDEX ix_files_site          ON files (site_id);
CREATE INDEX ix_files_owner         ON files (lower(owner_email));
CREATE INDEX ix_files_site_subject  ON files (site_id, subject_id);
CREATE INDEX ix_files_site_checksum ON files (site_id, checksum);
CREATE INDEX ix_files_trashed       ON files (site_id, trashed_at);

CREATE TABLE file_permissions (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    org_id       UUID        NOT NULL,
    site_id      UUID        NOT NULL,
    file_id      UUID        NOT NULL REFERENCES files (id) ON DELETE CASCADE,
    grantee      VARCHAR(255),
    target_type  VARCHAR(30) NOT NULL,
    access_level VARCHAR(20) NOT NULL,
    external     BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_file_permissions_file ON file_permissions (file_id);

CREATE TABLE file_snapshots (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ NOT NULL,
    org_id           UUID        NOT NULL,
    site_id          UUID        NOT NULL,
    file_id          UUID        NOT NULL REFERENCES files (id) ON DELETE CASCADE,
    drive_type       VARCHAR(20) NOT NULL,
    folder_path      VARCHAR(1000),
    sharing_summary  VARCHAR(2000),
    permission_count INTEGER     NOT NULL
);
CREATE INDEX ix_file_snapshots_file ON file_snapshots (file_id, created_at DESC);

CREATE TABLE file_profiles (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ      NOT NULL,
    org_id           UUID             NOT NULL,
    site_id          UUID             NOT NULL,
    file_id          UUID             NOT NULL UNIQUE REFERENCES files (id) ON DELETE CASCADE,
    doc_type         VARCHAR(100),
    subject_id       UUID,
    has_student_data BOOLEAN          NOT NULL DEFAULT FALSE,
    has_health_data  BOOLEAN          NOT NULL DEFAULT FALSE,
    sensitivity      VARCHAR(20)      NOT NULL,
    confidence       DOUBLE PRECISION NOT NULL,
    model_version    VARCHAR(50),
    needs_review     BOOLEAN          NOT NULL DEFAULT FALSE,
    classified_by    UUID
);
CREATE INDEX ix_file_profiles_review ON file_profiles (site_id, needs_review);

CREATE TABLE file_profile_evidence (
    file_profile_id UUID         NOT NULL REFERENCES file_profiles (id) ON DELETE CASCADE,
    evidence        VARCHAR(500)
);
CREATE INDEX ix_file_profile_evidence ON file_profile_evidence (file_profile_id);

-- ---------- rules ------------------------------------------------------------------

CREATE TABLE rules (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    org_id     UUID        NOT NULL REFERENCES orgs (id),
    code       VARCHAR(20) NOT NULL,
    site_id    UUID REFERENCES sites (id),   -- NULL = network-wide rule
    status     VARCHAR(20) NOT NULL
);
CREATE INDEX ix_rules_org_status ON rules (org_id, status);

CREATE TABLE rule_versions (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMPTZ   NOT NULL,
    org_id         UUID          NOT NULL,
    rule_id        UUID          NOT NULL REFERENCES rules (id),
    version_number INTEGER       NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    why_it_matters VARCHAR(1000) NOT NULL,
    why_it_matters_ar VARCHAR(1000) NOT NULL,
    check_type     VARCHAR(30)   NOT NULL,
    severity       VARCHAR(10)   NOT NULL,
    reminder_days  INTEGER       NOT NULL,
    parameter      VARCHAR(200),
    fix_steps_en   VARCHAR(2000) NOT NULL,
    fix_steps_ar   VARCHAR(2000) NOT NULL,
    proposed_by    UUID,
    approved_by    UUID,
    approved_at    TIMESTAMPTZ,
    UNIQUE (rule_id, version_number)
);

CREATE TABLE rule_version_doc_types (
    rule_version_id UUID         NOT NULL REFERENCES rule_versions (id) ON DELETE CASCADE,
    doc_type        VARCHAR(100) NOT NULL,
    PRIMARY KEY (rule_version_id, doc_type)
);

CREATE TABLE rule_version_sensitivities (
    rule_version_id UUID        NOT NULL REFERENCES rule_versions (id) ON DELETE CASCADE,
    sensitivity     VARCHAR(20) NOT NULL,
    PRIMARY KEY (rule_version_id, sensitivity)
);

-- ---------- issues -----------------------------------------------------------------

CREATE TABLE issues (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ   NOT NULL,
    org_id           UUID          NOT NULL,
    site_id          UUID          NOT NULL,
    file_id          UUID          NOT NULL REFERENCES files (id),
    rule_id          UUID          NOT NULL REFERENCES rules (id),
    rule_version_id  UUID          NOT NULL REFERENCES rule_versions (id),
    owner_id         UUID REFERENCES people (id),
    subject_id       UUID,
    status           VARCHAR(30)   NOT NULL,
    severity         VARCHAR(10)   NOT NULL,
    detail_en        VARCHAR(1000),
    detail_ar        VARCHAR(1000),
    opened_at        TIMESTAMPTZ   NOT NULL,
    closed_at        TIMESTAMPTZ,
    reminder_due_at  TIMESTAMPTZ,
    follow_up_due_at TIMESTAMPTZ,
    owner_note       VARCHAR(1000)
);
-- "An issue is one rule broken by one file": at most one OPEN issue per (file, rule).
CREATE UNIQUE INDEX uq_issues_open_file_rule ON issues (file_id, rule_id)
    WHERE status IN ('OWNER_NOTIFIED', 'REMINDER_SENT', 'NEEDS_FOLLOW_UP',
                     'NEEDS_REVIEW', 'OWNER_DISAGREES', 'OWNER_ASKED_FOR_HELP');
CREATE INDEX ix_issues_owner         ON issues (owner_id, status);
CREATE INDEX ix_issues_site_subject  ON issues (site_id, subject_id, status);
CREATE INDEX ix_issues_site_status   ON issues (site_id, status);
CREATE INDEX ix_issues_reminder_due  ON issues (reminder_due_at) WHERE reminder_due_at IS NOT NULL;
CREATE INDEX ix_issues_followup_due  ON issues (follow_up_due_at) WHERE follow_up_due_at IS NOT NULL;

CREATE TABLE issue_events (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ   NOT NULL,
    org_id      UUID          NOT NULL,
    site_id     UUID          NOT NULL,
    issue_id    UUID          NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    actor_id    UUID,
    event_type  VARCHAR(30)   NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30),
    note        VARCHAR(1000)
);
CREATE INDEX ix_issue_events_issue ON issue_events (issue_id, created_at);

CREATE TABLE exceptions (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ   NOT NULL,
    org_id     UUID          NOT NULL,
    site_id    UUID          NOT NULL,
    file_id    UUID          NOT NULL REFERENCES files (id),
    rule_id    UUID          NOT NULL REFERENCES rules (id),
    issue_id   UUID REFERENCES issues (id),
    subject_id UUID,
    reason     VARCHAR(1000) NOT NULL,
    granted_by UUID          NOT NULL,
    ends_on    DATE          NOT NULL,
    ended_at   TIMESTAMPTZ,
    ended_by   UUID
);
CREATE INDEX ix_exceptions_file_rule ON exceptions (file_id, rule_id);

-- ---------- messages, notifications, incidents -------------------------------------

CREATE TABLE messages (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ   NOT NULL,
    org_id       UUID          NOT NULL,
    site_id      UUID          NOT NULL,
    issue_id     UUID REFERENCES issues (id),
    recipient_id UUID          NOT NULL REFERENCES people (id),
    copy_to_id   UUID REFERENCES people (id),
    language     VARCHAR(10)   NOT NULL,
    subject      VARCHAR(300)  NOT NULL,
    body         VARCHAR(4000) NOT NULL,
    deliver_at   TIMESTAMPTZ   NOT NULL,
    sent_at      TIMESTAMPTZ
);
CREATE INDEX ix_messages_due ON messages (deliver_at) WHERE sent_at IS NULL;

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ   NOT NULL,
    org_id       UUID          NOT NULL,
    site_id      UUID          NOT NULL,
    recipient_id UUID          NOT NULL REFERENCES people (id),
    kind         VARCHAR(20)   NOT NULL,
    title        VARCHAR(300)  NOT NULL,
    body         VARCHAR(1000),
    issue_id     UUID,
    read_at      TIMESTAMPTZ
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, created_at DESC);

CREATE TABLE incidents (
    id                    UUID PRIMARY KEY,
    created_at            TIMESTAMPTZ   NOT NULL,
    org_id                UUID          NOT NULL,
    site_id               UUID          NOT NULL,
    type                  VARCHAR(30)   NOT NULL,
    file_id               UUID,
    issue_id              UUID,
    description           VARCHAR(1000) NOT NULL,
    detected_at           TIMESTAMPTZ   NOT NULL,
    leadership_alerted_at TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ
);
CREATE INDEX ix_incidents_org  ON incidents (org_id, detected_at DESC);
CREATE INDEX ix_incidents_site ON incidents (site_id, detected_at DESC);

-- ---------- activity log (append-only, hash-chained) -------------------------------

CREATE TABLE activity_log (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ   NOT NULL,
    org_id       UUID          NOT NULL,
    site_id      UUID          NOT NULL,
    seq          BIGINT        NOT NULL,
    actor_id     UUID,
    action       VARCHAR(100)  NOT NULL,
    entity_type  VARCHAR(60)   NOT NULL,
    entity_id    UUID,
    subject_id   UUID,
    before_state VARCHAR(2000),
    after_state  VARCHAR(2000),
    prev_hash    VARCHAR(64)   NOT NULL,
    hash         VARCHAR(64)   NOT NULL,
    UNIQUE (org_id, seq)
);
CREATE INDEX ix_activity_log_site    ON activity_log (site_id, seq DESC);
CREATE INDEX ix_activity_log_subject ON activity_log (site_id, subject_id, seq DESC);

-- The log must be unchangeable by the application's database role (section 8.1 "Log tampering").
CREATE FUNCTION activity_log_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'activity_log is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER activity_log_no_update_delete
    BEFORE UPDATE OR DELETE ON activity_log
    FOR EACH ROW EXECUTE FUNCTION activity_log_reject_change();

CREATE TRIGGER activity_log_no_truncate
    BEFORE TRUNCATE ON activity_log
    FOR EACH STATEMENT EXECUTE FUNCTION activity_log_reject_change();

-- ---------- planning-file review ---------------------------------------------------

CREATE TABLE planning_submissions (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ   NOT NULL,
    org_id             UUID          NOT NULL,
    site_id            UUID          NOT NULL,
    teacher_id         UUID          NOT NULL REFERENCES people (id),
    subject_id         UUID          NOT NULL REFERENCES subjects (id),
    grade              VARCHAR(20)   NOT NULL,
    week_start         DATE          NOT NULL,
    plan_type          VARCHAR(60)   NOT NULL,
    drive_link         VARCHAR(1000) NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    submitted_at       TIMESTAMPTZ   NOT NULL,
    resubmission_count INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_planning_teacher ON planning_submissions (teacher_id, week_start DESC);
CREATE INDEX ix_planning_subject ON planning_submissions (site_id, subject_id, week_start DESC);

CREATE TABLE planning_reviews (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ  NOT NULL,
    org_id        UUID         NOT NULL,
    site_id       UUID         NOT NULL,
    submission_id UUID         NOT NULL REFERENCES planning_submissions (id) ON DELETE CASCADE,
    reviewer_id   UUID         NOT NULL REFERENCES people (id),
    decision      VARCHAR(20)  NOT NULL,
    reason        VARCHAR(1000)
);
CREATE INDEX ix_planning_reviews_submission ON planning_reviews (submission_id, created_at);

-- ---------- restores, exports, runs, scores ----------------------------------------

CREATE TABLE restore_requests (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ   NOT NULL,
    org_id             UUID          NOT NULL,
    site_id            UUID          NOT NULL,
    file_id            UUID REFERENCES files (id),
    scope              VARCHAR(20)   NOT NULL,
    target             VARCHAR(500)  NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    requested_by       UUID          NOT NULL REFERENCES people (id),
    status             VARCHAR(20)   NOT NULL,
    first_approver_id  UUID REFERENCES people (id),
    second_approver_id UUID REFERENCES people (id),
    restored_at        TIMESTAMPTZ
);
CREATE INDEX ix_restore_site ON restore_requests (site_id, created_at DESC);

CREATE TABLE exports (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ  NOT NULL,
    org_id        UUID         NOT NULL,
    site_id       UUID         NOT NULL,
    exported_by   UUID         NOT NULL REFERENCES people (id),
    export_kind   VARCHAR(60)  NOT NULL,
    masked        BOOLEAN      NOT NULL DEFAULT TRUE,
    unmask_reason VARCHAR(500),
    row_count     INTEGER      NOT NULL
);

-- Check runs ("checks") and backup runs share one table: same lifecycle, same counters.
CREATE TABLE job_runs (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMPTZ   NOT NULL,
    org_id         UUID          NOT NULL,
    site_id        UUID          NOT NULL REFERENCES sites (id),
    job_type       VARCHAR(30)   NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    started_at     TIMESTAMPTZ   NOT NULL,
    finished_at    TIMESTAMPTZ,
    started_by     UUID,
    items_processed INTEGER      NOT NULL DEFAULT 0,
    items_skipped  INTEGER       NOT NULL DEFAULT 0,
    issues_opened  INTEGER       NOT NULL DEFAULT 0,
    issues_closed  INTEGER       NOT NULL DEFAULT 0,
    verified       BOOLEAN       NOT NULL DEFAULT FALSE,
    correlation_id VARCHAR(64),
    note           VARCHAR(4000)
);
CREATE INDEX ix_job_runs_site ON job_runs (site_id, job_type, started_at DESC);

CREATE TABLE health_scores (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMPTZ      NOT NULL,
    org_id        UUID             NOT NULL,
    site_id       UUID             NOT NULL REFERENCES sites (id),
    score_date    DATE             NOT NULL,
    score         DOUBLE PRECISION NOT NULL,
    files_checked INTEGER          NOT NULL,
    open_normal   INTEGER          NOT NULL,
    open_urgent   INTEGER          NOT NULL,
    UNIQUE (site_id, score_date)
);
