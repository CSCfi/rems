-- begin generating form IDs using form_template_id_seq instead of application_form_id_seq
SELECT setval('form_template_id_seq', (SELECT nextval('application_form_id_seq')), false);
--;;
ALTER TABLE catalogue_item
    DROP CONSTRAINT catalogue_item_ibfk_3;
