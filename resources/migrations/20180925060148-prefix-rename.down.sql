ALTER TABLE APPLICATION_FORM
RENAME COLUMN organization to prefix;
--;;
ALTER TABLE workflow
RENAME COLUMN organization to prefix;
--;;
ALTER TABLE resource
RENAME COLUMN organization to prefix;
