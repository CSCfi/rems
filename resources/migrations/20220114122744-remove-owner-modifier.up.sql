ALTER TABLE resource
DROP COLUMN IF EXISTS owneruserid,
DROP COLUMN IF EXISTS modifieruserid;
--;;
ALTER TABLE form_template
DROP COLUMN IF EXISTS owneruserid,
DROP COLUMN IF EXISTS modifieruserid;
--;;
ALTER TABLE license
DROP COLUMN IF EXISTS owneruserid,
DROP COLUMN IF EXISTS modifieruserid;
