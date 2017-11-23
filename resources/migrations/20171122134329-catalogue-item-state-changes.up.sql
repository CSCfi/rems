ALTER TABLE catalogue_item_state DROP COLUMN IF EXISTS id;
--;;
ALTER TABLE catalogue_item_state ADD PRIMARY KEY (catid);
--;;
