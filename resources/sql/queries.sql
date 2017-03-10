-- :name get-catalogue-items :? :*
SELECT rci.id, rci.title, rr.resid
FROM rms_catalogue_item rci
LEFT OUTER JOIN rms_resource rr ON (rci.resid = rr.id)

-- :name get-catalogue-item :? :1
SELECT rci.id, rci.title, rr.resid
FROM rms_catalogue_item rci
LEFT OUTER JOIN rms_resource rr ON (rci.resid = rr.id)
WHERE rci.id = :id

-- :name create-catalogue-item! :! :n
-- :doc Create a single catalogue item
INSERT INTO rms_catalogue_item
(title, formid, resid)
VALUES (:title, :formid, :resid)

-- :name create-resource! :! :n
-- :doc Create a single resource
INSERT INTO rms_resource
(id, resid, prefix, modifieruserid)
VALUES (:id, :resid, :prefix, :modifieruserid)

-- :name get-database-name :? :1
SELECT current_database()

-- :name get-catalogue-item-localizations :? :*
SELECT catid, langcode, title
FROM rms_catalogue_item_localization
