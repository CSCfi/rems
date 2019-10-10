ALTER TABLE workflow
DROP COLUMN start,
DROP COLUMN endt;
--;;
ALTER TABLE resource
DROP COLUMN start,
DROP COLUMN endt;
--;;
ALTER TABLE form_template
DROP COLUMN start,
DROP COLUMN endt;
--;;
CREATE UNIQUE INDEX resource_resid_u ON resource (organization, resid);
