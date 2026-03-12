ALTER TABLE accounts
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_accounts
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
