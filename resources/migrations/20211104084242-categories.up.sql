CREATE TABLE category (
  id serial NOT NULL PRIMARY KEY,
  categorydata jsonb,
  organization varchar(255)
);
--;;
ALTER TABLE catalogue_item
 ADD COLUMN catalogueitemdata jsonb;