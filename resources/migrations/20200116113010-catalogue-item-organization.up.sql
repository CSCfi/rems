ALTER TABLE catalogue_item
ADD COLUMN organization varchar(255);
--;;
UPDATE catalogue_item SET organization = '';
--;;
ALTER TABLE catalogue_item
ALTER COLUMN organization SET NOT NULL;
