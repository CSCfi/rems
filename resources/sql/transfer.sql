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

-- data created by the app that might reference data we want to clear
DELETE FROM public.application_text_values CASCADE;
DELETE FROM public.catalogue_item_application CASCADE;

-- clear existing data
DELETE FROM public.entitlement CASCADE;
DELETE FROM public.application_event CASCADE;
DELETE FROM public.workflow_actors CASCADE;
DELETE FROM public.workflow_licenses CASCADE;
DELETE FROM public.license_localization CASCADE;
DELETE FROM public.license CASCADE;
DELETE FROM public.catalogue_item_localization CASCADE;
DELETE FROM public.catalogue_item CASCADE;
DELETE FROM public.resource CASCADE;
DELETE FROM public.workflow CASCADE;
DELETE FROM public.application_form_item_map CASCADE;
DELETE FROM public.application_form_item CASCADE;
DELETE FROM public.application_form_meta_map CASCADE;
DELETE FROM public.application_form_meta CASCADE;
DELETE FROM public.application_form CASCADE;

INSERT INTO public.workflow
SELECT * FROM transfer.rms_workflow;

INSERT INTO public.resource (id, modifierUserId, prefix, resId, start, endt)
SELECT id, modifierUserId, prefix, resId, start, "end" FROM transfer.rms_resource;

INSERT INTO public.application_form
SELECT * FROM transfer.rms_application_form;

INSERT INTO public.application_form_meta
SELECT * FROM transfer.rms_application_form_meta;

INSERT INTO public.application_form_meta_map
SELECT * FROM transfer.rms_application_form_meta_map;

INSERT INTO public.application_form_item
SELECT * FROM transfer.rms_application_form_item;

INSERT INTO public.application_form_item_map
SELECT * FROM transfer.rms_application_form_item_map;

INSERT INTO public.catalogue_item
SELECT * FROM transfer.rms_catalogue_item;

INSERT INTO public.catalogue_item_localization
SELECT * FROM transfer.rms_catalogue_item_localization;

INSERT INTO public.license
SELECT * FROM transfer.rms_license;

INSERT INTO public.license_localization
SELECT * FROM transfer.rms_license_localization;

INSERT INTO public.workflow_licenses
SELECT * FROM transfer.rms_workflow_licenses;

INSERT INTO public.workflow_actors (wfId, actorUserId, role, round, start, endt)
SELECT wfId, apprUserId, 'approver' AS ROLE, round, start, "end" FROM transfer.rms_workflow_approvers;

INSERT INTO public.workflow_actors (wfId, actorUserId, role, round, start, endt)
SELECT wfId, revUserId, 'reviewer' AS ROLE, round, start, "end" FROM transfer.rms_workflow_reviewers;

INSERT INTO public.application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'approve' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'approved';

INSERT INTO public.application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'reject' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'rejected';

INSERT INTO public.application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'return' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'returned';

INSERT INTO public.application_event (appId, userId, round, event, comment, time)
SELECT catAppId, wfApprId, round, 'close' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_approvers
WHERE state = 'closed';

INSERT INTO public.application_event (appId, userId, round, event, comment, time)
SELECT catAppId, revUserId, round, 'review' AS EVENT, comment, start FROM transfer.rms_catalogue_item_application_reviewers
WHERE state = 'commented';

INSERT INTO public.application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'apply' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'applied';

INSERT INTO public.application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'approve' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'approved';

INSERT INTO public.application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'reject' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'rejected';

INSERT INTO public.application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'return' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'returned';

INSERT INTO public.application_event (appId, userId, round, event, time)
SELECT catAppId, modifierUserId, curround, 'close' AS EVENT, start FROM transfer.rms_catalogue_item_application_state
WHERE state = 'closed';

INSERT INTO public.entitlement
SELECT * FROM transfer.rms_entitlement;

-- if all casts are not dropped, the next pgloader run might fail
-- (can't drop a type that is referenced by a cast)
DROP CAST IF EXISTS (transfer.rms_workflow_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_meta_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_item_type AS public.itemtype);
DROP CAST IF EXISTS (transfer.rms_application_form_item_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_license_type AS public.license_type);
DROP CAST IF EXISTS (transfer.rms_license_visibility AS public.scope);
