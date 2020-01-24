ALTER TABLE license
ADD COLUMN organization varchar(255);
--;;
UPDATE license SET organization = '';
--;;
ALTER TABLE license
ALTER COLUMN organization SET NOT NULL;
