CREATE TABLE category (
  id serial NOT NULL PRIMARY KEY,
  categorydata jsonb
);
--;;
ALTER TABLE catalogue_item
 ADD COLUMN catalogueitemdata jsonb;