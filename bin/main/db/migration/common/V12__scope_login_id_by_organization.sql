ALTER TABLE user_accounts
    DROP CONSTRAINT IF EXISTS user_accounts_login_id_key;

ALTER TABLE user_accounts
    ADD CONSTRAINT uq_user_accounts_org_login UNIQUE (organization_id, login_id);

ALTER TABLE accounts
    DROP CONSTRAINT IF EXISTS accounts_login_id_key;

ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_org_login UNIQUE (organization_id, login_id);
