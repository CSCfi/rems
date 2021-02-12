ALTER TABLE form_template ADD COLUMN title varchar(256);
--;;
UPDATE form_template SET title = formdata->'form/internal-name';
--;;
ALTER TABLE form_template ALTER COLUMN title SET NOT NULL;
--;;
ALTER TABLE form_template DROP COLUMN formdata;
