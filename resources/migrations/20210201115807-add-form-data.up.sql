ALTER TABLE form_template ADD formdata jsonb;
--;;
UPDATE form_template SET formdata = jsonb '{"form/external-title": {}}' || jsonb_build_object('form/internal-name', title);
--;;
ALTER TABLE form_template DROP COLUMN title;
