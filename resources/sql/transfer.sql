CREATE CAST (transfer.rms_workflow_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

CREATE CAST (transfer.rms_application_form_meta_visibility AS public.scope)
WITH INOUT
AS IMPLICIT;

DELETE FROM public.rms_catalogue_item_localization CASCADE;
DELETE FROM public.rms_catalogue_item CASCADE;
DELETE FROM public.rms_resource CASCADE;
DELETE FROM public.rms_resource_prefix CASCADE;
DELETE FROM public.rms_workflow CASCADE;
DELETE FROM public.rms_application_form_meta CASCADE;

INSERT INTO public.rms_workflow
SELECT * FROM transfer.rms_workflow;

INSERT INTO public.rms_resource_prefix
SELECT * FROM transfer.rms_resource_prefix;

INSERT INTO public.rms_resource
SELECT * FROM transfer.rms_resource;

INSERT INTO public.rms_application_form_meta
SELECT * FROM transfer.rms_application_form_meta;

INSERT INTO public.rms_catalogue_item
SELECT * FROM transfer.rms_catalogue_item;

INSERT INTO public.rms_catalogue_item_localization
SELECT * FROM transfer.rms_catalogue_item_localization;

DROP CAST IF EXISTS (transfer.rms_workflow_visibility AS public.scope);
DROP CAST IF EXISTS (transfer.rms_application_form_meta_visibility AS public.scope);
