-- Invite-only onboarding: the school provisions accounts and issues one-time codes.
CREATE TABLE invitations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    site_id     UUID         NOT NULL,
    kind        VARCHAR(10)  NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL UNIQUE,
    email       VARCHAR(255),
    created_by  UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    used_by     UUID,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE TABLE invitation_students (
    invitation_id UUID NOT NULL REFERENCES invitations (id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    PRIMARY KEY (invitation_id, student_id)
);
CREATE INDEX idx_invitations_site ON invitations (site_id, created_at DESC);
