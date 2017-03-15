-- :name get-catalogue-items :? :*
SELECT rci.id, rci.title, rr.resid
FROM rms_catalogue_item rci
LEFT OUTER JOIN rms_resource rr ON (rci.resid = rr.id)

-- :name get-catalogue-item :? :1
SELECT rci.id, rci.title, rr.resid
FROM rms_catalogue_item rci
LEFT OUTER JOIN rms_resource rr ON (rci.resid = rr.id)
WHERE rci.id = :id

-- :name create-catalogue-item! :insert
-- :doc Create a single catalogue item
INSERT INTO rms_catalogue_item
(title, formid, resid)
VALUES (:title, :form, :resid)

-- :name create-resource! :insert
-- :doc Create a single resource
INSERT INTO rms_resource
(id, resid, prefix, modifieruserid)
VALUES (:id, :resid, :prefix, :modifieruserid)

-- :name get-database-name :? :1
SELECT current_database()

-- :name get-catalogue-item-localizations :? :*
SELECT catid, langcode, title
FROM rms_catalogue_item_localization

-- :name get-forms :? :*
SELECT
  meta.id as metaid,
  form.id as formid,
  meta.title as metatitle,
  form.title as formtitle,
  meta.visibility as metavisibility,
  form.visibility as formvisibility,
  langcode
FROM rms_application_form_meta meta
LEFT OUTER JOIN rms_application_form_meta_map metamap ON meta.id = metamap.metaFormId
LEFT OUTER JOIN rms_application_form form ON form.id = metamap.formId

-- :name get-form-for-catalogue-item :? :1
SELECT
  meta.id as metaid,
  form.id as formid,
  meta.title as metatitle,
  form.title as formtitle,
  meta.visibility as metavisibility,
  form.visibility as formvisibility,
  langcode
FROM rms_catalogue_item rci
LEFT OUTER JOIN rms_application_form_meta meta ON rci.formId = meta.id
LEFT OUTER JOIN rms_application_form_meta_map metamap ON meta.id = metamap.metaFormId
LEFT OUTER JOIN rms_application_form form ON form.id = metamap.formId
WHERE rci.id = :id
  AND (langcode = :lang
       OR langcode is NULL) -- nonlocalized form

-- :name get-form-items :? :*
SELECT
  item.id,
  item.title,
  inputprompt,
  formitemoptional,
  type,
  value,
  itemorder,
  tooltip,
  item.visibility
FROM rms_application_form form
LEFT OUTER JOIN rms_application_form_item_map itemmap ON form.id = itemmap.formId
LEFT OUTER JOIN rms_application_form_item item ON item.id = itemmap.formItemId
WHERE form.id = :id
ORDER BY itemorder

-- :name create-form! :insert
INSERT INTO rms_application_form
(title, modifierUserId, ownerUserId, visibility)
VALUES
(:title, :user, :user, 'public')

-- :name create-form-meta! :insert
INSERT INTO rms_application_form_meta
(title, ownerUserId, modifierUserId, visibility)
VALUES
(:title, :user, :user, 'public')

-- :name link-form-meta! :insert
INSERT INTO rms_application_form_meta_map
(metaFormId, formId, langcode, modifierUserId)
VALUES
(:meta, :form, :lang, :user)

-- :name create-form-item! :insert
INSERT INTO rms_application_form_item
(title, type, inputPrompt, value, modifierUserId, ownerUserId, visibility)
VALUES
(:title, CAST (:type as itemtype), :inputprompt, :value, :user, :user, 'public')

-- :name link-form-item! :insert
INSERT INTO rms_application_form_item_map
(formId, formItemId, modifierUserId, itemOrder)
VALUES
(:form, :item, :user, :itemorder)

-- :name create-application! :insert
-- TODO: what is fnlround?
INSERT INTO rms_catalogue_item_application
(catId, applicantUserId, fnlround)
VALUES
(:item, :user, 0)
RETURNING id

-- :name save-field-value! :!
-- TODO: upsert
INSERT INTO rms_application_text_values
(catAppId, modifierUserId, value, formMapId)
VALUES
(:application, :user, :value,
 (SELECT id FROM rms_application_form_item_map
  WHERE formId = :form AND formItemId = :item))

-- :name get-field-value :? :n
SELECT
  value
FROM rms_application_text_values textvalues
LEFT OUTER JOIN rms_application_form_item_map itemmap ON textvalues.formMapId = itemmap.id
WHERE textvalues.catAppId = :application
  AND itemmap.formItemId = :item
  AND itemmap.formId = :form
