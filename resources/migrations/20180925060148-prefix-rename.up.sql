ALTER TABLE APPLICATION_FORM
RENAME COLUMN prefix to organization;
--;;
ALTER TABLE workflow
RENAME COLUMN prefix to organization;
--;;
ALTER TABLE resource
RENAME COLUMN prefix to organization;
