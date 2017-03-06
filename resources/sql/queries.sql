-- :name get-catalogue-items :? :*
SELECT * FROM rms_catalogue_item

-- :name get-catalogue-item :? :1
SELECT * FROM rms_catalogue_item WHERE id = :id

-- :name create-test-data! :! :n
INSERT INTO rms_catalogue_item
(title, formId)
VALUES ('A', null), ('B', null)

-- :name get-database-name :? :1
SELECT current_database()
