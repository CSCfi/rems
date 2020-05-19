CREATE TABLE organization (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  modifierUserId varchar(255) NOT NULL,
  modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  data jsonb NOT NULL
);
