ALTER TABLE catalogue_item_localization
  ADD CONSTRAINT catalogue_item_localization_unique UNIQUE (catid, langcode);
