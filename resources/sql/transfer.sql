CREATE CAST (transfer.rms_workflow_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_application_form_meta_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_application_form_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_application_form_item_type AS public.itemtype)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_application_form_item_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_license_type AS public.license_type)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_license_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

CREATE TABLE transfer.migrated_application_event (
  id serial NOT NULL PRIMARY KEY, -- for ordering events
  appId integer REFERENCES catalogue_item_application (id),
  userId varchar(255) REFERENCES users (userId),
  round integer NOT NULL,
  event application_event_type NOT NULL,
  comment varchar(4096) DEFAULT NULL,
  time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- data created by the app that might reference data we want to clear
DELETE FROM public.entitlement CASCADE;
DELETE FROM public.application_text_values CASCADE;
DELETE FROM public.catalogue_item_application_items CASCADE;
DELETE FROM public.catalogue_item_application_licenses CASCADE;
DELETE FROM public.catalogue_item_application CASCADE;

-- clear existing data
DELETE FROM public.application_event CASCADE;
DELETE FROM public.workflow_actors CASCADE;
DELETE FROM public.workflow_licenses CASCADE;
DELETE FROM public.license_localization CASCADE;
DELETE FROM public.license CASCADE;
DELETE FROM public.catalogue_item_localization CASCADE;
DELETE FROM public.catalogue_item CASCADE;
DELETE FROM public.resource CASCADE;
DELETE FROM public.workflow CASCADE;
DELETE FROM public.application_form_item_localization CASCADE;
DELETE FROM public.application_form_item_map CASCADE;
DELETE FROM public.application_form_item CASCADE;
DELETE FROM public.application_form CASCADE;

INSERT INTO public.workflow
SELECT * FROM transfer.rms_workflow;

INSERT INTO public.resource (id, modifierUserId, prefix, resId, start, endt)
SELECT id, modifierUserId, prefix, resId, start, "end" FROM transfer.rms_resource;

-- forms

-- The old data localizes per-form, whereas we localize per-item. We
-- migrate the data so that behaviour of existing catalogue items
-- remains the same.
--
--                                       form -- form_item_map -- form_item
--                                      /
-- catalogue_item -- form_meta -- form_meta_map
--                                      \
--                                       form -- form_item_map -- form_item
--
--                        I                             I
--                        V                             V
--
--                                                        form_item_localization
--                                                      /
-- catalogue_item -- form -- form_item_map -- form_item
--                                                      \
--                                                        form_item_localization
--
-- Let's reuse the metaId as the formId, to make copying catalogue_item simpler

-- Create forms
INSERT INTO public.application_form (id, ownerUserId, modifierUserId, title, visibility, start, endt)
SELECT id, ownerUserId, modifierUserId, COALESCE(title,'unknown'), visibility, start, "end"
FROM transfer.rms_application_form_meta;

-- Create a table for form items
DROP TABLE IF EXISTS migrated_form_item;
CREATE TABLE migrated_form_item (
  metaId integer,
  langCode varchar(64),
  itemOrder integer,
  itemMapId integer,
  itemId integer
);

INSERT INTO migrated_form_item (metaId, langCode, itemOrder, itemMapId, itemId)
SELECT
  meta.id, metamap.langCode, itemmap.itemOrder, itemmap.id, item.id
FROM transfer.rms_application_form_meta meta
LEFT JOIN transfer.rms_application_form_meta_map metamap ON metamap.metaFormId = meta.id
LEFT JOIN transfer.rms_application_form form ON form.id = metamap.formId
LEFT JOIN transfer.rms_application_form_item_map itemmap ON itemmap.formId = form.id
LEFT JOIN transfer.rms_application_form_item item ON item.id = itemmap.formItemId;

-- Allocate item ids
DROP TABLE IF EXISTS migrated_item_ids;
CREATE TABLE migrated_item_ids (
  id serial,
  metaId integer,
  itemOrder integer
);

INSERT INTO migrated_item_ids (metaId, itemOrder)
SELECT DISTINCT metaId, itemOrder
FROM migrated_form_item;

-- Create form items, but only one per language
INSERT INTO public.application_form_item (id, type, value, visibility, ownerUserId, modifierUserId, start, endt)
SELECT
  DISTINCT ON (mig.metaId, mig.itemOrder)
  ids.id,
  item.type,
  item.value,
  item.visibility,
  item.ownerUserId,
  item.modifierUserId,
  item.start,
  item.end
FROM migrated_form_item mig
LEFT JOIN migrated_item_ids ids ON ids.metaId = mig.metaId AND ids.itemOrder = mig.itemOrder
LEFT JOIN transfer.rms_application_form_item item ON item.id = mig.itemId;

-- Create form-item mappings
INSERT INTO public.application_form_item_map (formId, formItemId, itemOrder, formItemOptional, modifierUserId, start, endt)
SELECT
  DISTINCT ON (mig.metaId, mig.itemOrder)
  mig.metaId, -- becomes formId
  ids.id,
  mig.itemOrder,
  itemmap.formItemOptional,
  itemmap.modifierUserId,
  itemmap.start,
  itemmap.end
FROM migrated_form_item mig
LEFT JOIN migrated_item_ids ids ON ids.metaId = mig.metaId AND ids.itemOrder = mig.itemOrder
LEFT JOIN transfer.rms_application_form_item_map itemmap ON itemmap.id = mig.itemMapId;

-- Create localizations
INSERT INTO public.application_form_item_localization
  (itemId, langCode, title, toolTip, inputPrompt)
SELECT
  ids.id,
  mig.langCode,
  item.title,
  item.toolTip,
  item.inputPrompt
FROM public.application_form_item_map itemmap
LEFT JOIN migrated_form_item mig ON mig.metaId = itemmap.formId AND mig.itemOrder = itemmap.itemOrder
LEFT JOIN migrated_item_ids ids ON ids.metaId = mig.metaId AND ids.itemOrder = mig.itemOrder
LEFT JOIN transfer.rms_application_form_item item ON item.id = mig.itemId;

-- catalogue items

INSERT INTO public.catalogue_item
SELECT * FROM transfer.rms_catalogue_item;

INSERT INTO public.catalogue_item_localization
SELECT * FROM transfer.rms_catalogue_item_localization;

-- licenses

INSERT INTO public.license
SELECT * FROM transfer.rms_license;

INSERT INTO public.license_localization
SELECT * FROM transfer.rms_license_localization;

INSERT INTO public.workflow_licenses
SELECT * FROM transfer.rms_workflow_licenses;

-- actors

INSERT INTO public.workflow_actors (wfId, actorUserId, role, round, start, endt)
SELECT wfId, apprUserId, 'approver' AS ROLE, round, start, "end" FROM transfer.rms_workflow_approvers;

INSERT INTO public.workflow_actors (wfId, actorUserId, role, round, start, endt)
SELECT wfId, revUserId, 'reviewer' AS ROLE, round, start, "end" FROM transfer.rms_workflow_reviewers;

-- applications

INSERT INTO public.catalogue_item_application (id, start, endt, applicantUserId, modifierUserId, wfid)
SELECT cia.id, cia.start, cia.end, cia.applicantUserId, cia.modifierUserId, item.wfid
FROM transfer.rms_catalogue_item_application cia
LEFT JOIN transfer.rms_catalogue_item item ON cia.catId = item.id;

-- events

-- create fake users so that application_event foreign keys work
-- TODO proper user migration
INSERT INTO users (userId)
SELECT wfApprId FROM transfer.rms_catalogue_item_application_approvers
UNION
SELECT revUserId FROM transfer.rms_catalogue_item_application_reviewers
UNION
SELECT modifierUserId FROM transfer.rms_catalogue_item_application_state;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'approve' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'approved';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'reject' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'rejected';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'return' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'returned';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'close' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'closed';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, revUserId, round, 'review' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_reviewers
WHERE state = 'commented';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'apply' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'applied';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'approve' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'approved';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'reject' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'rejected';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'return' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'returned';

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'close' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'closed';

INSERT INTO public.application_event
SELECT * FROM transfer.migrated_application_event
ORDER BY time;

-- entitlements

INSERT INTO public.entitlement
SELECT * FROM transfer.rms_entitlement;

-- if all casts are not dropped, the next pgloader run might fail
-- (can't drop a type that is referenced by a cast)
DROP TABLE IF EXISTS transfer.migrated_application_event CASCADE;
DROP CAST IF EXISTS (transfer.rms_workflow_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_meta_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_item_type AS public.itemtype);
DROP CAST IF EXISTS (transfer.rms_application_form_item_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_license_type AS public.license_type);
DROP CAST IF EXISTS (transfer.rms_license_visibility AS public.scope);
