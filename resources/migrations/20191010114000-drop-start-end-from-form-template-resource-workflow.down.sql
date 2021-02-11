ALTER TABLE workflow
ADD COLUMN start timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN endt timestamp with time zone NULL DEFAULT NULL;
--;;
ALTER TABLE resource
ADD COLUMN start timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN endt timestamp with time zone NULL DEFAULT NULL;
--;;
ALTER TABLE form_template
ADD COLUMN start timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN endt timestamp with time zone NULL DEFAULT NULL;
--;;
DROP INDEX IF EXISTS resource_resid_u;
--;;
-- We used to create a better resource_resid_u constraint here, but don't anymore.
-- See rationale in 20200204064800-drop-unique-resid.down.sql
