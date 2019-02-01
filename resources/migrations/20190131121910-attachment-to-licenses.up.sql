CREATE TABLE license_attachment (
    id serial NOT NULL PRIMARY KEY,
    modifierUserId varchar(255) NOT NULL,
    filename varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    data bytea NOT NULL,
    start timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);
--;;
ALTER TABLE license_localization ADD COLUMN attachmentId INTEGER REFERENCES license_attachment;
--;;
ALTER TABLE license ADD COLUMN attachmentId INTEGER REFERENCES license_attachment;