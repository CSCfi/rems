-- Add localizations for catalogue items with a title but missing localizations.
INSERT INTO catalogue_item_localization(catid, title, langcode)
SELECT ci.id, ci.title, 'en'
FROM catalogue_item ci FULL OUTER JOIN catalogue_item_localization loc
ON ci.id=loc.catid
WHERE loc.catid IS NULL;
--;;
ALTER TABLE catalogue_item DROP COLUMN title;
