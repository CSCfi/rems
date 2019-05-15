-- No migration for data!
CREATE TABLE attachment (
    id serial NOT NULL PRIMARY KEY,
    appId integer NOT NULL, -- link to application for access control
    modifierUserId varchar(255) NOT NULL,
    filename varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    data bytea NOT NULL,
    CONSTRAINT attachment_appid_fkey FOREIGN KEY (appId) REFERENCES catalogue_item_application (id)
);
--;;
DROP TABLE IF EXISTS application_attachments CASCADE;
--;;
