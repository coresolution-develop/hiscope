ALTER TABLE organizations
    ADD COLUMN organization_profile VARCHAR(30) NOT NULL DEFAULT 'HOSPITAL_DEFAULT';

UPDATE organizations
SET organization_profile = CASE
    WHEN organization_type = 'HOSPITAL' THEN 'HOSPITAL_DEFAULT'
    WHEN organization_type = 'AFFILIATE' THEN 'AFFILIATE_GENERAL'
    ELSE 'HOSPITAL_DEFAULT'
END
WHERE organization_profile IS NULL
   OR organization_profile = '';

