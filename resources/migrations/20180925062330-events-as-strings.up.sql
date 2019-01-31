ALTER TABLE application_event
  ALTER COLUMN event TYPE varchar(32);
--;;
DROP TYPE application_event_type CASCADE;
