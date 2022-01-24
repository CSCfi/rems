ALTER TABLE resource
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE form_template
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE license
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE workflow
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE organization
DROP COLUMN IF EXISTS modifierUserId,
DROP COLUMN IF EXISTS modified;
--;;
ALTER TABLE attachment
RENAME COLUMN modifierUserId TO userId;
--;;
ALTER TABLE license_attachment
RENAME COLUMN modifierUserId TO userId;
