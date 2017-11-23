ALTER TABLE catalogue_item_state DROP CONSTRAINT catalogue_item_state_pkey;
--;;
ALTER TABLE catalogue_item_state ADD COLUMN id;
--;;
ALTER TABLE catalogue_item_state ADD PRIMARY KEY (id);
--;;
