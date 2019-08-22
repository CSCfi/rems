ALTER TABLE catalogue_item ADD title varchar(256);
--;; XXX: Do not copy title from localizations but leave it empty.
UPDATE catalogue_item SET title='';
--;;
ALTER TABLE catalogue_item ALTER COLUMN title SET NOT NULL;
