-- Four-eyes principle: a sensitive action is requested by one person and carried out only when a different person approves it.
CREATE TABLE approval_requests (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            UUID         NOT NULL,
    site_id           UUID         NOT NULL,
    action            VARCHAR(40)  NOT NULL,
    target_id         UUID,
    payload           VARCHAR(1000),
    summary           VARCHAR(300) NOT NULL,
    requested_by      UUID         NOT NULL,
    requested_by_name VARCHAR(200) NOT NULL,
    status            VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    decided_by        UUID,
    decided_by_name   VARCHAR(200),
    decided_at        TIMESTAMPTZ,
    decision_note     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_approvals_org_status ON approval_requests (org_id, status);
CREATE INDEX idx_approvals_site ON approval_requests (site_id);
