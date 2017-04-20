-- :name get-catalogue-items :? :*
SELECT ci.id, ci.title, res.resid
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)

-- :name get-catalogue-item :? :1
SELECT ci.id, ci.title, res.resid
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

-- :name get-form-for-catalogue-item :? :1
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
WHERE ci.id = :id
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
-- TODO: what is fnlround?
INSERT INTO catalogue_item_application
(catId, applicantUserId, fnlround)
VALUES
(:item, :user, 0)
RETURNING id

-- :name update-application-state! :!
INSERT INTO catalogue_item_application_state
(catAppId, modifierUserId, curround, state)
VALUES
(:id, :user, :curround, CAST (:state as application_state))
ON CONFLICT (catAppId)
DO UPDATE
SET (modifierUserId, curround, state) = (:user, :curround, CAST (:state as application_state))

-- :name get-applications :? :*
-- :doc
-- - Pass in no arguments to get all applications.
-- - Use {:id id} to get a specific application
-- - Use {:resource id} to get applications for a specific resource
-- - Use {:state state} to filter by application state
-- - Use {:applicant user} to filter by applicant
-- - Use {:approver user} to filter by possible approver
SELECT
  app.id, app.catId, app.applicantUserId, app.start, state.state, state.curround, wf.fnlround
FROM catalogue_item_application app
LEFT OUTER JOIN catalogue_item_application_state state ON app.id = state.catAppId
LEFT OUTER JOIN catalogue_item cat ON app.catid = cat.id
LEFT OUTER JOIN workflow wf ON cat.wfid = wf.id
/*~ (when (:approver params) */
LEFT OUTER JOIN workflow_approvers wfa ON wf.id = wfa.wfid
/*~ ) ~*/
WHERE 1=1
/*~ (when (:id params) */
  AND app.id = :id
/*~ ) ~*/
/*~ (when (:resource params) */
  AND app.catId = :resource
/*~ ) ~*/
/*~ (when (:state params) */
  AND state.state = CAST (:state AS application_state)
/*~ ) ~*/
/*~ (when (:applicant params) */
  AND app.applicantUserId = :applicant
/*~ ) ~*/
/*~ (when (:approver params) */
  AND wfa.apprUserId = :approver
  AND state.curround = wfa.round
/*~ ) ~*/

-- :name add-application-approval! :!
-- TODO: This table is denormalized and bad. Should either only have
-- wfApprId (and not apprUserId and round) or, my favourite, get rid
-- of the workflow_approvers.id column all together.
INSERT INTO catalogue_item_application_approvers
  (catAppId, wfApprId, apprUserId, round, comment, state)
SELECT
  :id, wfa.id, :user, :round, :comment, CAST (:state AS approval_status)
FROM catalogue_item_application app
LEFT OUTER JOIN catalogue_item cat ON app.catid = cat.id
LEFT OUTER JOIN workflow wf ON cat.wfid = wf.id
LEFT OUTER JOIN workflow_approvers wfa ON wf.id = wfa.wfid
WHERE app.id = :id
  AND wfa.apprUserId = :user
  AND wfa.round = :round

-- :name get-application-approvals :? :*
SELECT
 *
FROM catalogue_item_application_approvers
WHERE catAppId = :application
/*~ (when (:round params) */
  AND round = :round
/*~ ) ~*/

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

-- :name create-workflow-approver! :insert
INSERT INTO workflow_approvers
(wfid, appruserid, round)
VALUES
(:wfid, :appruserid, :round)

-- :name get-workflow-approvers :? :n
SELECT
  wfa.appruserid
FROM workflow_approvers wfa
LEFT OUTER JOIN workflow wf on wf.id = wfa.wfid
LEFT OUTER JOIN catalogue_item cat ON cat.wfid = wf.id
LEFT OUTER JOIN catalogue_item_application app ON app.catid = cat.id
WHERE app.id = :application
  AND wfa.round = :round

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

-- :name get-workflow-licenses :? :*
SELECT
  lic.id, lic.title, lic.type, lic.textcontent
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licId
INNER JOIN catalogue_item cat ON wl.wfId = cat.wfId
WHERE cat.id = :catId

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
