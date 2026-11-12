-- Stored OAuth2 tokens (Spring Security's JdbcOAuth2AuthorizedClientService schema, PostgreSQL types).
-- Lets the nightly and change-feed checks keep reading Drive after the person who signed in has gone:
-- the refresh token is used to get a new access token.
CREATE TABLE oauth2_authorized_client (
    client_registration_id  VARCHAR(100) NOT NULL,
    principal_name          VARCHAR(200) NOT NULL,
    access_token_type       VARCHAR(100) NOT NULL,
    access_token_value      BYTEA        NOT NULL,
    access_token_issued_at  TIMESTAMP    NOT NULL,
    access_token_expires_at TIMESTAMP    NOT NULL,
    access_token_scopes     VARCHAR(1000) DEFAULT NULL,
    refresh_token_value     BYTEA        DEFAULT NULL,
    refresh_token_issued_at TIMESTAMP    DEFAULT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_registration_id, principal_name)
);

-- Which signed-in account the connector reads Drive as (set when that account signs in).
CREATE TABLE connector_account (
    provider       VARCHAR(50)  PRIMARY KEY,
    principal_name VARCHAR(200) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
