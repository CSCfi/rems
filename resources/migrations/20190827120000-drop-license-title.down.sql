ALTER TABLE license ADD title varchar(256) DEFAULT '' NOT NULL;
--;;
ALTER TABLE license ALTER COLUMN title DROP DEFAULT;
--;;
ALTER TABLE license ADD textcontent varchar(16384) DEFAULT NULL;
--;;
ALTER TABLE license ADD attachmentid integer DEFAULT NULL;
