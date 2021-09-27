CREATE TABLE category (
  id serial NOT NULL PRIMARY KEY,
  data jsonb,
  organization varchar(255)
);

ALTER TABLE catalogue_item
 ADD COLUMN categories jsonb;