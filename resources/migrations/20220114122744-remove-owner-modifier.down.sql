ALTER TABLE resource
ADD COLUMN ownerUserId varchar(255),
ADD COLUMN modifierUserId varchar(255);
-- NB: don't set constraint back
--;;
ALTER TABLE form_template
ADD COLUMN ownerUserId varchar(255),
ADD COLUMN modifierUserId varchar(255);
--;;
ALTER TABLE license
ADD COLUMN ownerUserId varchar(255),
ADD COLUMN modifierUserId varchar(255);
--;;
ALTER TABLE workflow
ADD COLUMN ownerUserId varchar(255),
ADD COLUMN modifierUserId varchar(255);
--;;
ALTER TABLE organization
ADD COLUMN modifierUserId varchar(255),
ADD COLUMN modified timestamp DEFAULT CURRENT_TIMESTAMP;
--;;
ALTER TABLE attachment
RENAME COLUMN userId TO modifierUserId;
--;;
ALTER TABLE license_attachment
RENAME COLUMN userId TO modifierUserId;
