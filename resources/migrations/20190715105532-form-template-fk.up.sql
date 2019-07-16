ALTER TABLE catalogue_item
    ADD CONSTRAINT catalogue_item_ibfk_4 FOREIGN KEY (formId) REFERENCES form_template (id);
