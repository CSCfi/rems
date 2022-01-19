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
--;;
ALTER TABLE workflow
DROP COLUMN IF EXISTS owneruserid,
DROP COLUMN IF EXISTS modifieruserid;
--;;
ALTER TABLE organization
DROP COLUMN IF EXISTS modifieruserid,
DROP COLUMN IF EXISTS modified;
--;;
ALTER TABLE attachment
RENAME COLUMN modifierUserId TO userId;
--;;
ALTER TABLE license_attachment
RENAME COLUMN modifierUserId TO userId;
