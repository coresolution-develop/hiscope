-- Fix affiliate profile backfill gap from V14.
-- V14 added organization_profile with NOT NULL DEFAULT, so legacy AFFILIATE rows
-- could remain HOSPITAL_DEFAULT depending on DDL semantics.
UPDATE organizations
SET organization_profile = 'AFFILIATE_GENERAL'
WHERE organization_type = 'AFFILIATE'
  AND organization_profile = 'HOSPITAL_DEFAULT';
