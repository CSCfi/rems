ALTER TABLE license
  DROP COLUMN start,
  DROP COLUMN endt;
--;;
ALTER TABLE resource_licenses
  DROP COLUMN start,
  DROP COLUMN endt;
--;;
ALTER TABLE workflow_licenses
  DROP COLUMN start,
  DROP COLUMN endt;
