-- EAA support tickets: a head teacher writes to head office; EAA staff triage, reply and close.
CREATE TABLE support_tickets (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id            UUID         NOT NULL,
    site_id           UUID         NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(4000) NOT NULL,
    category          VARCHAR(20)  NOT NULL,
    urgency           VARCHAR(10)  NOT NULL,
    status            VARCHAR(12)  NOT NULL DEFAULT 'OPEN',
    created_by        UUID         NOT NULL,
    created_by_name   VARCHAR(200) NOT NULL,
    assigned_to       UUID,
    assigned_to_name  VARCHAR(200),
    attachment_name   VARCHAR(200),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ
);
CREATE INDEX idx_tickets_org_status ON support_tickets (org_id, status, updated_at DESC);
CREATE INDEX idx_tickets_site ON support_tickets (site_id, updated_at DESC);

-- The optional attachment is kept apart so listing tickets never loads file contents.
CREATE TABLE support_ticket_attachments (
    ticket_id    UUID PRIMARY KEY REFERENCES support_tickets (id) ON DELETE CASCADE,
    content_type VARCHAR(100) NOT NULL,
    data         BYTEA        NOT NULL
);

CREATE TABLE support_ticket_replies (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL,
    site_id      UUID         NOT NULL,
    ticket_id    UUID         NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    author_id    UUID         NOT NULL,
    author_name  VARCHAR(200) NOT NULL,
    author_role  VARCHAR(20)  NOT NULL,
    body         VARCHAR(4000) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_replies_ticket ON support_ticket_replies (ticket_id, created_at);

-- Personal settings: contact number, profile photo and the kinds of alert a person has switched off.
ALTER TABLE people ADD COLUMN phone VARCHAR(30);
ALTER TABLE people ADD COLUMN photo BYTEA;
ALTER TABLE people ADD COLUMN photo_type VARCHAR(30);
ALTER TABLE people ADD COLUMN muted_kinds VARCHAR(200) NOT NULL DEFAULT '';
