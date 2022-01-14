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
