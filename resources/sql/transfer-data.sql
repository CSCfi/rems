DROP TABLE IF EXISTS transfer.migrated_application_event;
DROP TABLE IF EXISTS transfer.migrated_form_item;
DROP TABLE IF EXISTS transfer.migrated_item_ids;

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
DELETE FROM public.catalogue_item_application_licenses CASCADE;
DELETE FROM public.catalogue_item_application_items CASCADE;
DELETE FROM public.catalogue_item_application_licenses CASCADE;
DELETE FROM public.application_event CASCADE;
DELETE FROM public.catalogue_item_application CASCADE;

-- clear existing data
DELETE FROM public.workflow_actors CASCADE;
DELETE FROM public.resource_licenses CASCADE;
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

INSERT INTO public.workflow (id, owneruserid, modifieruserid, title, fnlround, visibility, start, endt)
SELECT id, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(owneruserid AS integer)), (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifieruserid AS integer)), title, fnlround, CAST(visibility::text AS scope), start, "end" FROM transfer.rms_workflow;

INSERT INTO public.resource (id, modifierUserId, prefix, resId, start, endt)
SELECT id, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifierUserId AS integer)), prefix, resId, start, "end" FROM transfer.rms_resource;

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
SELECT id, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(owneruserid AS integer)), (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifieruserid AS integer)), COALESCE(title,'unknown'), CAST(visibility::text AS scope), start, "end"
FROM transfer.rms_application_form_meta;

-- Create a table for form items
CREATE TABLE transfer.migrated_form_item (
  metaId integer,
  langCode varchar(64),
  itemOrder integer,
  itemMapId integer,
  itemId integer
);

INSERT INTO transfer.migrated_form_item (metaId, langCode, itemOrder, itemMapId, itemId)
SELECT
  meta.id, metamap.langCode, itemmap.itemOrder, itemmap.id, item.id
FROM transfer.rms_application_form_meta meta
LEFT JOIN transfer.rms_application_form_meta_map metamap ON metamap.metaFormId = meta.id
LEFT JOIN transfer.rms_application_form form ON form.id = metamap.formId
LEFT JOIN transfer.rms_application_form_item_map itemmap ON itemmap.formId = form.id
LEFT JOIN transfer.rms_application_form_item item ON item.id = itemmap.formItemId;

-- Allocate item ids
CREATE TABLE transfer.migrated_item_ids (
  id serial,
  metaId integer,
  itemOrder integer
);

INSERT INTO transfer.migrated_item_ids (metaId, itemOrder)
SELECT DISTINCT metaId, itemOrder
FROM transfer.migrated_form_item;

-- Create form items, but only one per language
INSERT INTO public.application_form_item (id, type, value, visibility, ownerUserId, modifierUserId, start, endt)
SELECT
  DISTINCT ON (mig.metaId, mig.itemOrder)
  ids.id,
  CAST(item.type::text AS itemtype),
  item.value,
  CAST(item.visibility::text AS scope),
  (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(item.owneruserid AS integer)),
  (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(item.modifieruserid AS integer)),
  item.start,
  item.end
FROM transfer.migrated_form_item mig
LEFT JOIN transfer.migrated_item_ids ids ON ids.metaId = mig.metaId AND ids.itemOrder = mig.itemOrder
LEFT JOIN transfer.rms_application_form_item item ON item.id = mig.itemId;

-- Create form-item mappings
INSERT INTO public.application_form_item_map (formId, formItemId, itemOrder, formItemOptional, modifierUserId, start, endt)
SELECT
  DISTINCT ON (mig.metaId, mig.itemOrder)
  mig.metaId, -- becomes formId
  ids.id,
  mig.itemOrder,
  itemmap.formItemOptional,
  (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(itemmap.modifieruserid AS integer)),
  itemmap.start,
  itemmap.end
FROM transfer.migrated_form_item mig
LEFT JOIN transfer.migrated_item_ids ids ON ids.metaId = mig.metaId AND ids.itemOrder = mig.itemOrder
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
LEFT JOIN transfer.migrated_form_item mig ON mig.metaId = itemmap.formId AND mig.itemOrder = itemmap.itemOrder
LEFT JOIN transfer.migrated_item_ids ids ON ids.metaId = mig.metaId AND ids.itemOrder = mig.itemOrder
LEFT JOIN transfer.rms_application_form_item item ON item.id = mig.itemId;

-- catalogue items

INSERT INTO public.catalogue_item
SELECT * FROM transfer.rms_catalogue_item;

INSERT INTO public.catalogue_item_localization
SELECT * FROM transfer.rms_catalogue_item_localization;

UPDATE public.catalogue_item
SET state = (SELECT CAST(cis.state::text AS item_state)
             FROM transfer.rms_catalogue_item_state cis
             WHERE catalogue_item.id = cis.catid
             ORDER BY cis.start DESC
             LIMIT 1);

-- licenses

INSERT INTO public.license (id, owneruserid, modifieruserid, title, type, textcontent, attid, visibility, start, endt)
SELECT id,
  (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(owneruserid AS integer)),
  (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifieruserid AS integer)),
  title,
  CAST(type::text AS license_type),
  textcontent,
  attid,
  CAST(visibility::text AS scope),
  start,
  "end"
FROM transfer.rms_license;

INSERT INTO public.license_localization
SELECT * FROM transfer.rms_license_localization;

INSERT INTO public.workflow_licenses
SELECT * FROM transfer.rms_workflow_licenses;

INSERT INTO public.resource_licenses (resId, licId, stalling, start, endt)
SELECT resId, licId, stalling, start, "end" FROM transfer.rms_resource_licenses;

-- actors

INSERT INTO public.workflow_actors (wfId, actorUserId, role, round, start, endt)
SELECT wfId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(apprUserId AS integer)), 'approver' AS ROLE, round, start, "end" FROM transfer.rms_workflow_approvers;

INSERT INTO public.workflow_actors (wfId, actorUserId, role, round, start, endt)
SELECT wfId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(revUserId AS integer)), 'reviewer' AS ROLE, round, start, "end" FROM transfer.rms_workflow_reviewers;

-- roles

INSERT INTO public.roles (userId, role)
SELECT DISTINCT userId, 'applicant' AS role
FROM transfer.user_mapping
WHERE userId IS NOT NULL;

INSERT INTO public.roles (userId, role)
SELECT userId, role
FROM (SELECT DISTINCT (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(apprUserId AS integer)) AS userId,
      'approver' AS role
       FROM transfer.rms_workflow_approvers) approvers
WHERE userId IS NOT NULL;

INSERT INTO public.roles (userId, role)
SELECT userId, role
FROM (SELECT DISTINCT (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(revUserId AS integer)) AS userId,
             'reviewer' AS role
      FROM transfer.rms_workflow_reviewers) reviewers
WHERE userId IS NOT NULL;

-- applications

INSERT INTO public.catalogue_item_application (id, start, endt, applicantUserId, modifierUserId, wfid)
SELECT cia.id, cia.start, cia.end, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(cia.applicantUserId AS integer)), (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(cia.modifierUserId AS integer)), item.wfid
FROM transfer.rms_catalogue_item_application cia
LEFT JOIN transfer.rms_catalogue_item item ON cia.catId = item.id;

INSERT INTO public.catalogue_item_application_items (catAppId, catItemId)
SELECT id, catId FROM transfer.rms_catalogue_item_application
UNION
SELECT catAppId, catId FROM transfer.rms_catalogue_item_application_catid_overflow;

-- approved application licenses

INSERT INTO public.catalogue_item_application_licenses (catAppId, licId, actorUserId, round, stalling, state, start, endt)
SELECT catAppId, licId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(actorUserId AS integer)), round, stalling, CAST(state::text AS license_state), start, "end" FROM transfer.rms_catalogue_item_application_licenses;

INSERT INTO public.application_license_approval_values (catAppId, licId, modifierUserId, state, start, endt)
SELECT
  catAppId,
  licId,
  (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifierUserId AS integer)),
  CAST(state::text AS license_status),
  start,
  "end"
FROM transfer.rms_application_license_approval_values
-- skip migrating license form item approvals:
WHERE formMapId IS NULL;

-- events

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(wfApprId AS integer)), round, 'approve' AS EVENT, comment, start
FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'approved' AND round >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(wfApprId AS integer)), round, 'reject' AS EVENT, comment, start
FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'rejected' AND round >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(wfApprId AS integer)), round, 'return' AS EVENT, comment, start
FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'returned' AND round >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(wfApprId AS integer)), round, 'close' AS EVENT, comment, start
FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'closed' AND round >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, comment, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(revUserId AS integer)), round, 'review' AS EVENT, comment, start
FROM transfer.rms_catalogue_item_application_reviewers
WHERE state = 'commented' AND round >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifierUserId AS integer)), curround, 'apply' AS EVENT, start
FROM transfer.rms_catalogue_item_application_state
WHERE state = 'applied' AND curround >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifierUserId AS integer)), curround, 'reject' AS EVENT, start
FROM transfer.rms_catalogue_item_application_state
WHERE state = 'rejected' AND curround >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifierUserId AS integer)), curround, 'return' AS EVENT, start
FROM transfer.rms_catalogue_item_application_state
WHERE state = 'returned' AND curround >= 0;

INSERT INTO transfer.migrated_application_event (appId, userId, round, event, time)
SELECT catAppId, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(modifierUserId AS integer)), curround, 'close' AS EVENT, start
FROM transfer.rms_catalogue_item_application_state
WHERE state = 'closed' AND curround >= 0;

INSERT INTO public.application_event (appId, userId, round, event, comment, time)
SELECT appId, userId, round, event, comment, time
FROM transfer.migrated_application_event
ORDER BY time;

-- entitlements

INSERT INTO public.entitlement (id, resid, catappid, userid, start, endt)
SELECT id, resid, catappid, (SELECT userId FROM transfer.user_mapping WHERE expandoId = CAST(rms_entitlement.userid AS integer)), start, "end" FROM transfer.rms_entitlement;
