CREATE TABLE catalogue_item_state (
  id serial NOT NULL PRIMARY KEY,
  catId integer DEFAULT NULL,
  modifierUserId varchar(255) NOT NULL,
  state item_state DEFAULT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_state_ibfk_1 FOREIGN KEY (catId) REFERENCES catalogue_item (id)
);
--;;
ALTER TABLE catalogue_item DROP COLUMN state;
