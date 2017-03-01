-- :name get-catalogue-items :? :*
SELECT * FROM rms_catalogue_item

-- :name create-test-data! :! :n
INSERT INTO rms_catalogue_item
(title, formId)
VALUES ('A', null), ('B', null)

-- :name get-database-name :? :1
SELECT current_database()
