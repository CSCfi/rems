-- :name get-catalogue-items :? :*
-- :doc
-- - Get catalogue items
-- - :items vector of item ids
-- - :resource resource id to fetch items for
SELECT ci.id, ci.title, res.resid, ci.wfid, ci.formid, ci.state
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
WHERE 1=1
/*~ (when (:items params) */
  AND ci.id IN (:v*:items)
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.resid = :resource
/*~ ) ~*/


-- :name get-catalogue-item :? :1
SELECT ci.id, ci.title, res.resid, ci.wfid, ci.formid, ci.state
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
WHERE ci.id = :item

-- :name set-catalogue-item-state! :insert
-- :doc Set catalogue item state enabled or disabled
UPDATE catalogue_item ci
SET state = CAST(:state AS item_state)
WHERE ci.id = :item

-- :name create-catalogue-item! :insert
-- :doc Create a single catalogue item
INSERT INTO catalogue_item
(title, formid, resid, wfid)
VALUES (:title, :form, :resid, :wfid)

-- :name get-resources :? :*
SELECT
  id,
  modifieruserid,
  prefix,
  resid,
  start,
  endt
FROM resource

-- :name create-resource! :insert
-- :doc Create a single resource
INSERT INTO resource
(resid, prefix, modifieruserid)
VALUES (:resid, :prefix, :modifieruserid)

-- :name get-database-name :? :1
SELECT current_database()

-- :name get-catalogue-item-localizations :? :*
SELECT catid AS id, langcode, title
FROM catalogue_item_localization

-- :name create-catalogue-item-localization! :insert
INSERT INTO catalogue_item_localization
  (catid, langcode, title)
VALUES (:id, :langcode, :title)

-- :name get-forms :? :*
SELECT
  id,
  title
FROM application_form

-- :name get-form-for-application :? :1
SELECT
  form.id as formid,
  form.title as formtitle,
  form.visibility as formvisibility
FROM catalogue_item_application_items ciai
LEFT OUTER JOIN catalogue_item ci ON ci.id = ciai.catItemId
LEFT OUTER JOIN application_form form ON form.id = ci.formId
WHERE ciai.catAppId = :application

-- :name get-form-for-item :? :1
SELECT
  form.id as formid,
  form.title as formtitle,
  form.visibility as formvisibility
FROM catalogue_item ci
LEFT OUTER JOIN application_form form ON form.id = ci.formId
WHERE ci.id = :item

-- :name get-form-items :? :*
SELECT
  item.id,
  formitemoptional,
  type,
  value,
  itemorder,
  item.visibility
FROM application_form form
LEFT OUTER JOIN application_form_item_map itemmap ON form.id = itemmap.formId
LEFT OUTER JOIN application_form_item item ON item.id = itemmap.formItemId
WHERE form.id = :id
ORDER BY itemorder

-- :name get-form-item-localizations :? :*
SELECT
  langCode,
  title,
  inputprompt
FROM application_form_item_localization
WHERE 1=1
/*~ (when (:item params) */
  AND itemId = :item
/*~ ) ~*/

-- :name create-form! :insert
INSERT INTO application_form
(title, modifierUserId, ownerUserId, visibility)
VALUES
(:title, :user, :user, 'public')

-- :name create-form-item! :insert
INSERT INTO application_form_item
(type, value, modifierUserId, ownerUserId, visibility)
VALUES
(CAST (:type as itemtype), :value, :user, :user, 'public')

-- :name link-form-item! :insert
INSERT INTO application_form_item_map
(formId, formItemId, modifierUserId, itemOrder, formItemOptional)
VALUES
(:form, :item, :user, :itemorder, :optional)

-- :name localize-form-item! :insert
INSERT INTO application_form_item_localization
(itemId, langCode, title, inputPrompt)
VALUES
(:item, :langcode, :title, :inputprompt)

-- :name create-application! :insert
INSERT INTO catalogue_item_application
(applicantUserId, wfid, start)
VALUES
/*~ (if (:start params) */
(:user, :wfid, :start)
/*~*/
(:user, :wfid, now())
/*~ ) ~*/
RETURNING id

-- :name add-application-item! :insert
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
-- - Use {:application id} to pass application
SELECT
  catAppId AS application,
  catItemId AS item
FROM catalogue_item_application_items ciai
WHERE 1=1
/*~ (when (:application params) */
  AND ciai.catAppId = :application
/*~ ) ~*/

-- :name add-entitlement! :!
-- TODO remove resId from this table to make it normalized?
INSERT INTO entitlement (catAppId, userId, resId)
SELECT :application, :user, cat.resid
FROM catalogue_item_application_items ciai
LEFT OUTER JOIN catalogue_item_application app ON ciai.catAppId = app.id
LEFT OUTER JOIN catalogue_item cat ON ciai.catItemId = cat.id
WHERE ciai.catAppId = :application

-- :name end-entitlement! :!
UPDATE entitlement
SET endt = current_timestamp
WHERE catAppId = :application

-- :name get-entitlements :?
-- :doc
-- Params:
--   :application -- application id to limit select to
--   :user -- user id to limit select to
--   :resource -- resid to limit select to
SELECT res.resId, catAppId, entitlement.userId, entitlement.start, users.userAttrs->>'mail' AS mail FROM entitlement
LEFT OUTER JOIN resource res ON entitlement.resId = res.id
LEFT OUTER JOIN users on entitlement.userId = users.userId
WHERE 1=1
/*~ (when (:application params) */
  AND catAppId = :application
/*~ ) ~*/
/*~ (when (:user params) */
  AND entitlement.userId = :user
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.resId = :resource
/*~ ) ~*/

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
-- NB: this is not atomic
INSERT INTO application_license_approval_values
(catappid,formmapid, licid, modifieruserid, state)
SELECT
:catappid, NULL as formmapid, :licid, :actoruserid, CAST(:state AS license_status)
WHERE NOT exists
(SELECT id, catappid, licid, modifieruserid
FROM application_license_approval_values
WHERE catappid = :catappid AND licid = :licid AND modifieruserid = :actoruserid);

-- :name delete-license-approval! :!
DELETE FROM application_license_approval_values
WHERE catappid = :catappid AND licid = :licid AND modifieruserid = :actoruserid

-- :name get-application-license-approval :? :1
SELECT state FROM application_license_approval_values
WHERE catappid = :catappid AND licid = :licid AND modifieruserid = :actoruserid

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

-- TODO: consider renaming this to link-resource-license!
-- :name create-resource-license! :insert
INSERT INTO resource_licenses
(resid, licid)
VALUES
(:resid, :licid)

-- :name set-resource-license-validity! :insert
-- :doc set license expiration
UPDATE resource_licenses rl
SET start = :start, endt = :end
WHERE rl.licid = :licid

-- :name set-workflow-license-validity! :insert
-- :doc set license expiration
UPDATE workflow_licenses wl
SET start = :start, endt = :end
WHERE wl.licid = :licid

-- :name create-workflow-actor! :insert
INSERT INTO workflow_actors
(wfid, actoruserid, role, round)
VALUES
(:wfid, :actoruserid, CAST (:role as workflow_actor_role), :round)

-- :name get-actors-for-applications :? :*
-- :doc
-- Get actors, joined with applications
-- - :wfid filter by workflow
-- - :application filter by application
-- - :round filter by round
-- - :role filter by role
SELECT
  wfa.actoruserid,
  wfa.role,
  app.id
/*~ (when (:wfid params) */
, wfa.round
/*~ ) ~*/
FROM workflow_actors wfa
LEFT OUTER JOIN workflow wf on wf.id = wfa.wfid
LEFT OUTER JOIN catalogue_item_application app ON app.wfid = wf.id
WHERE 1=1
/*~ (when (:application params) */
  AND app.id = :application
/*~ ) ~*/
/*~ (when (:wfid params) */
  AND wfa.wfid = :wfid
/*~ ) ~*/
/*~ (when (:round params) */
  AND wfa.round = :round
/*~ ) ~*/
/*~ (when (:role params) */
  AND wfa.role = CAST (:role as workflow_actor_role)
/*~ ) ~*/

-- :name get-workflow-actors :? :*
SELECT
  actoruserid, role, round
FROM workflow_actors
WHERE wfid = :wfid

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

-- :name get-workflows :? :*
SELECT
  wf.id, wf.owneruserid, wf.modifieruserid, wf.title, wf.fnlround, wf.visibility, wf.start, wf.endt
FROM workflow wf

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

-- :name get-licenses :? :*
-- :doc
-- - Gets application licenses by workflow and catalogue item ids
-- - :wfid workflow id for workflow licenses
-- - :items vector of catalogue item ids for resource licenses
SELECT lic.id, lic.title, lic.type, lic.textcontent, wl.start, wl.endt
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licid
WHERE wl.wfid = :wfid
UNION
SELECT lic.id, lic.title, lic.type, lic.textcontent, rl.start, rl.endt
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
WHERE item.id IN (:v*:items)

-- :name get-resource-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent, rl.start, rl.endt
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
WHERE rl.resid = :id

-- :name get-all-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent
FROM license lic

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

-- TODO: consider refactoring this into get-application-events
-- :name get-all-application-events :? :*
SELECT
  appId, userId, round, event, comment, time
FROM application_event
ORDER BY id ASC

-- :name add-application-event! :insert
INSERT INTO application_event (appId, userId, round, event, comment)
VALUES (:application, :user, :round, CAST (:event AS application_event_type), :comment)

-- :name get-application-event-types :? :*
SELECT unnest(enum_range(NULL::application_event_type));

-- :name get-application-states :? :*
SELECT unnest(enum_range(NULL::application_state));

-- :name log-entitlement-post! :insert
INSERT INTO entitlement_post_log (payload, status)
VALUES (:payload::jsonb, :status);

-- :name add-api-key! :insert
INSERT INTO api_key (apiKey, comment)
VALUES (:apikey, :comment)

-- :name get-api-key :? :1
SELECT apiKey FROM api_key
WHERE apiKey = :apikey
