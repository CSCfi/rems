-- lost data cannot be reinstated
ALTER TABLE license
  ADD COLUMN start timestamp with time zone NOT NULL DEFAULT now(),
  ADD COLUMN endt timestamp with time zone;
--;;
ALTER TABLE resource_licenses
  ADD COLUMN start timestamp with time zone NOT NULL DEFAULT now(),
  ADD COLUMN endt timestamp with time zone;
--;;
ALTER TABLE workflow_licenses
  ADD COLUMN start timestamp with time zone NOT NULL DEFAULT now(),
  ADD COLUMN endt timestamp with time zone;
