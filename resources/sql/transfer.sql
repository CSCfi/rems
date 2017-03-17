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

DELETE FROM public.catalogue_item_localization CASCADE;
DELETE FROM public.catalogue_item CASCADE;
DELETE FROM public.resource CASCADE;
DELETE FROM public.resource_prefix CASCADE;
DELETE FROM public.workflow CASCADE;
DELETE FROM public.application_form_item_map CASCADE;
DELETE FROM public.application_form_item CASCADE;
DELETE FROM public.application_form_meta_map CASCADE;
DELETE FROM public.application_form_meta CASCADE;
DELETE FROM public.application_form CASCADE;

INSERT INTO public.workflow
SELECT * FROM transfer.rms_workflow;

INSERT INTO public.resource_prefix
SELECT * FROM transfer.rms_resource_prefix;

INSERT INTO public.resource
SELECT * FROM transfer.rms_resource;

INSERT INTO public.application_form
SELECT * FROM transfer.rms_application_form;

INSERT INTO public.application_form_meta
SELECT * FROM transfer.rms_application_form_meta;

INSERT INTO public.application_form_meta_map
SELECT * FROM transfer.rms_application_form_meta_map;

INSERT INTO public.application_form_item
SELECT * FROM transfer.rms_application_form_item;

INSERT INTO public.application_form_item_map
SELECT
  id,
  formId,
  formItemId,
  CASE WHEN formItemOptional THEN b'1'
       ELSE b'0'
  END
  AS formItemOptional,
  modifierUserId,
  itemOrder,
  start,
  transfer.rms_application_form_item_map.end AS endt
FROM transfer.rms_application_form_item_map;

INSERT INTO public.catalogue_item
SELECT * FROM transfer.rms_catalogue_item;

INSERT INTO public.catalogue_item_localization
SELECT * FROM transfer.rms_catalogue_item_localization;

DROP CAST IF EXISTS (transfer.rms_workflow_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_meta_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_item_type AS public.itemtype);
