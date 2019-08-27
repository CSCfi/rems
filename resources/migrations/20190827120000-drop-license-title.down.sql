ALTER TABLE license ADD title varchar(256);
--;; XXX: Do not copy title from localizations but leave it empty.
UPDATE license SET title='';
--;;
ALTER TABLE license ALTER COLUMN title SET NOT NULL;
--;;
ALTER TABLE license ADD textcontent varchar(16384) DEFAULT NULL;
--;;
ALTER TABLE license ADD attachmentid integer DEFAULT NULL;
