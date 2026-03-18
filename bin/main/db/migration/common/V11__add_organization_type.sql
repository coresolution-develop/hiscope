ALTER TABLE organizations
    ADD COLUMN organization_type VARCHAR(20) NOT NULL DEFAULT 'HOSPITAL';

UPDATE organizations
SET organization_type = 'HOSPITAL'
WHERE organization_type IS NULL;

