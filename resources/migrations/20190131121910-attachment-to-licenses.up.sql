CREATE TABLE license_attachment (
    id serial NOT NULL PRIMARY KEY,
    filename varchar(255) NOT NULL,
    data bytea NOT NULL,
);


ALTER TABLE license_localization
  ADD COLUMN attachmentId REFERENCES license_attachments(id);