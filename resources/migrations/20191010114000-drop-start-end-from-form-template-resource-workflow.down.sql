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
DROP INDEX resource_resid_u;
--;;
CREATE UNIQUE INDEX resource_resid_u ON resource (organization, resid, coalesce(endt, '10000-01-01'));
