CREATE TABLE category (
  id serial NOT NULL PRIMARY KEY,
  data jsonb,
  organization varchar(255)
  -- modifierUserId varchar(255),
  -- ownerUserId varchar(255),
);