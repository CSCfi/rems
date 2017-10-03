-- :name get-catalogue-items :? :*
SELECT ci.id, ci.title, res.resid, ci.wfid
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
WHERE 1=1
/*~ (when (:items params) */
  AND ci.id IN (:v*:items)
/*~ ) ~*/


-- :name get-catalogue-item :? :1
SELECT ci.id, ci.title, res.resid, ci.wfid
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
WHERE ci.id = :id

-- :name create-catalogue-item! :insert
-- :doc Create a single catalogue item
INSERT INTO catalogue_item
(title, formid, resid, wfid)
VALUES (:title, :form, :resid, :wfid)

-- :name create-resource! :insert
-- :doc Create a single resource
INSERT INTO resource
(id, resid, prefix, modifieruserid)
VALUES (:id, :resid, :prefix, :modifieruserid)

-- :name get-database-name :? :1
SELECT current_database()

-- :name get-catalogue-item-localizations :? :*
SELECT catid, langcode, title
FROM catalogue_item_localization

-- :name create-catalogue-item-localization! :insert
INSERT INTO catalogue_item_localization
  (catid, langcode, title)
VALUES (:id, :langcode, :title)

-- :name get-forms :? :*
SELECT
  meta.id as metaid,
  form.id as formid,
  meta.title as metatitle,
  form.title as formtitle,
  meta.visibility as metavisibility,
  form.visibility as formvisibility,
  langcode
FROM application_form_meta meta
LEFT OUTER JOIN application_form_meta_map metamap ON meta.id = metamap.metaFormId
LEFT OUTER JOIN application_form form ON form.id = metamap.formId

-- :name get-form-for-application :? :1
SELECT
  meta.id as metaid,
  form.id as formid,
  meta.title as metatitle,
  form.title as formtitle,
  meta.visibility as metavisibility,
  form.visibility as formvisibility,
  langcode
FROM catalogue_item_application_items ciai
LEFT OUTER JOIN catalogue_item ci ON ci.id = ciai.catItemId
LEFT OUTER JOIN application_form_meta meta ON ci.formId = meta.id
LEFT OUTER JOIN application_form_meta_map metamap ON meta.id = metamap.metaFormId
LEFT OUTER JOIN application_form form ON form.id = metamap.formId
WHERE ciai.catAppId = :application
  AND (langcode = :lang
       OR langcode is NULL) -- nonlocalized form

-- :name get-form-for-item :? :1
SELECT
  meta.id as metaid,
  form.id as formid,
  meta.title as metatitle,
  form.title as formtitle,
  meta.visibility as metavisibility,
  form.visibility as formvisibility,
  langcode
FROM catalogue_item ci
LEFT OUTER JOIN application_form_meta meta ON ci.formId = meta.id
LEFT OUTER JOIN application_form_meta_map metamap ON meta.id = metamap.metaFormId
LEFT OUTER JOIN application_form form ON form.id = metamap.formId
WHERE ci.id = :item
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
FROM application_form form
LEFT OUTER JOIN application_form_item_map itemmap ON form.id = itemmap.formId
LEFT OUTER JOIN application_form_item item ON item.id = itemmap.formItemId
WHERE form.id = :id
ORDER BY itemorder

-- :name create-form! :insert
INSERT INTO application_form
(title, modifierUserId, ownerUserId, visibility)
VALUES
(:title, :user, :user, 'public')

-- :name create-form-meta! :insert
INSERT INTO application_form_meta
(title, ownerUserId, modifierUserId, visibility)
VALUES
(:title, :user, :user, 'public')

-- :name link-form-meta! :insert
INSERT INTO application_form_meta_map
(metaFormId, formId, langcode, modifierUserId)
VALUES
(:meta, :form, :lang, :user)

-- :name create-form-item! :insert
INSERT INTO application_form_item
(title, type, inputPrompt, value, modifierUserId, ownerUserId, visibility)
VALUES
(:title, CAST (:type as itemtype), :inputprompt, :value, :user, :user, 'public')

-- :name link-form-item! :insert
INSERT INTO application_form_item_map
(formId, formItemId, modifierUserId, itemOrder, formItemOptional)
VALUES
(:form, :item, :user, :itemorder, :optional)

-- :name create-application! :insert
INSERT INTO catalogue_item_application
(applicantUserId, wfid)
VALUES
(:user, :wfid)
RETURNING id

-- :name add-catalogue-item! :insert
INSERT INTO catalogue_item_application_items
(catAppId, catItemId)
VALUES
(:application, :item)
RETURNING catAppId, catItemId

-- :name get-applications :? :*
-- :doc
-- - Pass in no arguments to get all applications.
-- - Use {:id id} to get a specific application
-- - Use {:applicant user} to filter by applicant
SELECT
  app.id, app.applicantUserId, app.start, wf.id as wfid, wf.fnlround
FROM catalogue_item_application app
--LEFT OUTER JOIN catalogue_item cat ON app.catid = cat.id
LEFT OUTER JOIN workflow wf ON app.wfid = wf.id
WHERE 1=1
/*~ (when (:id params) */
  AND app.id = :id
/*~ ) ~*/
/*~ (when (:applicant params) */
  AND app.applicantUserId = :applicant
/*~ ) ~*/

-- :name get-application-items :? :*
-- :doc
-- - Use {:application id} to get pass application
SELECT
  catAppId AS application,
  catItemId AS item
FROM catalogue_item_application_items ciai
WHERE 1=1
  AND ciai.catAppId = :application


/* TODO think these through wrt bundling
-- :name add-entitlement! :!
-- TODO remove resId from this table to make it normalized?
INSERT INTO entitlement
  (catAppId, userId, resId)
VALUES
  (:application, :user,
   (SELECT
      cat.resid
    FROM catalogue_item_application app
    LEFT OUTER JOIN catalogue_item cat ON app.catid = cat.id
    WHERE app.id = :application))

-- :name get-entitlements :?
SELECT resId, catAppId, userId FROM entitlement
*/

-- :name save-field-value! :!
INSERT INTO application_text_values
(catAppId, modifierUserId, value, formMapId)
VALUES
(:application, :user, :value,
 (SELECT id FROM application_form_item_map
  WHERE formId = :form AND formItemId = :item))
ON CONFLICT (catAppId, formMapId)
DO UPDATE
SET (modifierUserId, value) = (:user, :value)

-- :name save-license-approval! :!
-- TODO: make this atomic
INSERT INTO catalogue_item_application_licenses
(catappid, licid, actoruserid, round, state)
SELECT
:catappid, :licid, :actoruserid, :round, CAST(:state AS license_state)
WHERE NOT exists
(SELECT id, catappid, licid, actoruserid
 FROM catalogue_item_application_licenses
 WHERE
 catappid = :catappid AND licid = :licid AND actoruserid = :actoruserid);

-- :name delete-license-approval! :!
DELETE FROM catalogue_item_application_licenses
WHERE catappid = :catappid AND licid = :licid AND actoruserid = :actoruserid

-- :name get-application-license-approval :? :1
SELECT state FROM catalogue_item_application_licenses
WHERE catappid = :catappid AND licid = :licid AND actoruserid = :actoruserid

-- :name create-license! :insert
INSERT INTO license
(ownerUserId, modifierUserId, title, type, textcontent)
VALUES
(:owneruserid, :modifieruserid, :title, :type::license_type, :textcontent)

-- :name create-license-localization! :insert
INSERT INTO license_localization
(licid, langcode, title, textcontent)
VALUES
(:licid, :langcode, :title, :textcontent)

-- :name create-workflow! :insert
INSERT INTO workflow
(ownerUserId, modifierUserId, title, fnlround)
VALUES
(:owneruserid, :modifieruserid, :title, :fnlround)

-- :name create-workflow-license! :insert
INSERT INTO workflow_licenses
(wfid, licid, round)
VALUES
(:wfid, :licid, :round)

-- :name create-workflow-actor! :insert
INSERT INTO workflow_actors
(wfid, actoruserid, role, round)
VALUES
(:wfid, :actoruserid, CAST (:role as workflow_actor_role), :round)

-- :name get-workflow-actors :? :*
SELECT
  wfa.actoruserid,
  wfa.role
/*~ (when (:wfid params) */
, wfa.round
/*~ ) ~*/
FROM workflow_actors wfa
/*~ (when (:application params) */
LEFT OUTER JOIN workflow wf on wf.id = wfa.wfid
LEFT OUTER JOIN catalogue_item_application app ON app.wfid = wf.id
WHERE app.id = :application
/*~ ) ~*/
/*~ (when (:wfid params) */
FROM workflow_actors wfa
WHERE wfa.wfid = :wfid
/*~ ) ~*/
/*~ (when (:round params) */
  AND wfa.round = :round
/*~ ) ~*/
/*~ (when (:role params) */
  AND wfa.role = CAST (:role as workflow_actor_role)
/*~ ) ~*/

-- :name get-workflow :? :1
SELECT
  wf.id, wf.owneruserid, wf.modifieruserid, wf.title, wf.fnlround, wf.visibility, wf.start, wf.endt
FROM workflow wf
/*~ (when (:catid params) */
JOIN catalogue_item ci ON (wf.id = ci.wfid)
/*~ ) ~*/
WHERE 1=1
/*~ (when (:wfid params) */
AND wf.id = :wfid
/*~ ) ~*/
/*~ (when (:catid params) */
AND ci.id = :catid
/*~ ) ~*/

-- :name clear-field-value! :!
DELETE FROM application_text_values
WHERE catAppId = :application
  AND formMapId = (SELECT id FROM application_form_item_map
                   WHERE formId = :form AND formItemId = :item)

-- :name get-field-value :? :n
SELECT
  value
FROM application_text_values textvalues
LEFT OUTER JOIN application_form_item_map itemmap ON textvalues.formMapId = itemmap.id
WHERE textvalues.catAppId = :application
  AND itemmap.formItemId = :item
  AND itemmap.formId = :form

-- :name get-application-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licid
INNER JOIN catalogue_item_application app ON (wl.wfid = app.wfid)
WHERE app.id = :id
UNION
SELECT lic.id, lic.title, lic.type, lic.textcontent
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
INNER JOIN catalogue_item_application_items ciai ON (ciai.catitemid = item.id)
WHERE ciai.catappid = :id

-- :name get-draft-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licid
WHERE wl.wfid = :wfid
UNION
SELECT lic.id, lic.title, lic.type, lic.textcontent
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
WHERE item.id IN (:v*:items)

-- :name get-license-localizations :? :*
SELECT licid, langcode, title, textcontent
FROM license_localization

-- :name get-roles :? :*
SELECT role
FROM roles
WHERE userId = :user

-- :name add-role! :!
INSERT INTO roles (userId, role)
VALUES (:user, :role)
ON CONFLICT (userId, role)
DO NOTHING

-- :name get-active-role :? :1
SELECT role
FROM active_role
WHERE userId = :user

-- :name set-active-role! :!
INSERT INTO active_role (userId, role)
VALUES (:user, :role)
ON CONFLICT (userId)
DO UPDATE SET role = :role

-- :name add-user! :!
INSERT INTO users (userId, userAttrs)
VALUES (:user, :userattrs::jsonb)
ON CONFLICT (userId)
DO UPDATE SET userAttrs = :userattrs::jsonb

-- :name get-users :? :*
SELECT userId
FROM users

-- :name get-user-attributes :? :1
SELECT userAttrs::TEXT
FROM users
WHERE userId = :user

-- :name get-application-events :? :*
SELECT
  userId, round, event, comment, time
FROM application_event
WHERE appId = :application
ORDER BY id ASC

-- :name add-application-event! :insert
INSERT INTO application_event (appId, userId, round, event, comment)
VALUES (:application, :user, :round, CAST (:event AS application_event_type), :comment)
