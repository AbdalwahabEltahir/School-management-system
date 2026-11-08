CREATE TABLE files (
    id                UUID PRIMARY KEY,
    org_id            UUID NOT NULL,
    site_id           UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    workspace_file_id VARCHAR(255) NOT NULL UNIQUE,
    name              VARCHAR(500) NOT NULL,
    owner_email       VARCHAR(255),
    sensitivity       VARCHAR(20)
);