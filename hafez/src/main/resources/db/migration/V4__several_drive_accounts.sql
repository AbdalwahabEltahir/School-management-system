-- The connector can read several Google accounts: one row per account instead of one per provider.
ALTER TABLE connector_account DROP CONSTRAINT connector_account_pkey;
ALTER TABLE connector_account ADD PRIMARY KEY (provider, email);
