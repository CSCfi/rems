-- :name get-catalogue-items :? :*
-- :doc
-- - Get catalogue items
-- - :ids vector of item ids
-- - :resource resource id to fetch items for
-- - :archived true if archived items should be included
SELECT ci.id, ci.title, res.resid, ci.wfid, ci.formid, ci.start, ci.endt as "end", ci.enabled, ci.archived
, (case when ci.enabled = true then 'enabled' else 'disabled' end) as state -- TODO: remove state
, res.id AS "resource-id"
/*~ (when (:expand-names? params) */
, wf.title AS "workflow-name"
, res.resid AS "resource-name"
, form.title AS "form-name"
/*~ ) ~*/
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
/*~ (when (:expand-names? params) */
LEFT OUTER JOIN workflow wf ON (ci.wfid = wf.id)
LEFT OUTER JOIN application_form form ON (ci.formid = form.id)
/*~ ) ~*/
WHERE 1=1
/*~ (when (:ids params) */
  AND ci.id IN (:v*:ids)
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.resid = :resource
/*~ ) ~*/
/*~ (when (not (:archived params)) */
  AND ci.archived = false
/*~ ) ~*/
;

-- :name get-catalogue-item :? :1
SELECT ci.id, ci.title, res.resid, ci.wfid, ci.formid, ci.start, ci.endt as "end", ci.enabled, ci.archived
, (case when ci.enabled = true then 'enabled' else 'disabled' end) as state -- TODO: remove state
, res.id AS "resource-id"
, wf.title AS "workflow-name"
, res.resid AS "resource-name"
, form.title AS "form-name"
FROM catalogue_item ci
LEFT OUTER JOIN resource res ON (ci.resid = res.id)
LEFT OUTER JOIN workflow wf ON (ci.wfid = wf.id)
LEFT OUTER JOIN application_form form ON (ci.formid = form.id)
WHERE ci.id = :id;

-- :name set-catalogue-item-state! :insert
UPDATE catalogue_item
SET
/*~ (when (boolean? (:enabled params)) */
  enabled = :enabled,
/*~ ) ~*/
/*~ (when (boolean? (:archived params)) */
  archived = :archived,
/*~ ) ~*/
  id = id
WHERE id = :id;

-- :name create-catalogue-item! :insert
-- :doc Create a single catalogue item
INSERT INTO catalogue_item
(title, formid, resid, wfid, enabled)
VALUES (:title, :form, :resid, :wfid,
/*~ (if (= "disabled" (:state params)) */  false /*~*/ true /*~ ) ~*/ -- TODO: remove state
);

-- :name get-resources :? :*
SELECT
  id,
  owneruserid,
  modifieruserid,
  organization,
  resid,
  start,
  endt,
  enabled,
  archived
FROM resource;

-- :name get-resource :? :1
SELECT
  id,
  owneruserid,
  modifieruserid,
  organization,
  resid,
  start,
  endt,
  enabled,
  archived
FROM resource
WHERE id = :id;

-- :name create-resource! :insert
-- :doc Create a single resource
INSERT INTO resource
(resid, organization, ownerUserId, modifieruserid, endt)
VALUES (:resid, :organization, :owneruserid, :modifieruserid,
 /*~ (if (:endt params) */ :endt /*~*/ NULL /*~ ) ~*/
);

-- :name set-resource-state! :insert
UPDATE resource
SET
  /*~ (when (boolean? (:enabled params)) */
  enabled = :enabled,
  /*~ ) ~*/
  /*~ (when (boolean? (:archived params)) */
  archived = :archived,
  /*~ ) ~*/
  id = id
WHERE id = :id;

-- :name get-database-name :? :1
SELECT current_database();

-- :name get-catalogue-item-localizations :? :*
SELECT catid AS id, langcode, title
FROM catalogue_item_localization;

-- :name create-catalogue-item-localization! :insert
INSERT INTO catalogue_item_localization
  (catid, langcode, title)
VALUES (:id, :langcode, :title);

-- :name get-forms :? :*
SELECT
  id,
  organization,
  title,
  start,
  endt,
  enabled,
  archived
FROM application_form;

-- :name get-form-for-application :? :1
SELECT
  form.id as formid,
  form.organization as organization,
  form.title as formtitle,
  form.visibility as formvisibility
FROM catalogue_item_application_items ciai
LEFT OUTER JOIN catalogue_item ci ON ci.id = ciai.catItemId
LEFT OUTER JOIN application_form form ON form.id = ci.formId
WHERE ciai.catAppId = :application;

-- :name get-form-for-item :? :1
SELECT
  form.id as formid,
  form.title as formtitle,
  form.visibility as formvisibility
FROM catalogue_item ci
LEFT OUTER JOIN application_form form ON form.id = ci.formId
WHERE ci.id = :item;

-- :name get-form-items :? :*
SELECT
  item.id,
  formitemoptional,
  type,
  value,
  itemorder,
  item.visibility,
  itemmap.maxlength
FROM application_form form
LEFT OUTER JOIN application_form_item_map itemmap ON form.id = itemmap.formId
LEFT OUTER JOIN application_form_item item ON item.id = itemmap.formItemId
WHERE form.id = :id AND item.id IS NOT NULL
ORDER BY itemorder;

-- :name get-form :? :1
SELECT
  form.id as id,
  form.organization as organization,
  form.title as title,
  form.start as start,
  form.endt as "end",
  TRUE as "active", -- TODO implement
  form.enabled,
  form.archived,
  (SELECT json_agg(joined)
   FROM (SELECT *,
                (SELECT json_agg(formitemlocalization)
                 FROM application_form_item_localization formitemlocalization
                 WHERE (formitemmap.formitemid = formitemlocalization.itemid)
                 GROUP BY formitemlocalization.itemid)  AS localizations
         FROM application_form_item_map formitemmap
         JOIN application_form_item formitem ON (formitemmap.formitemid = formitem.id)
         WHERE formitemmap.formid = form.id) joined)::TEXT
         AS fields
FROM application_form form
WHERE form.id = :id;

-- :name get-all-form-items :? :*
SELECT id, type, value, visibility, start, endt, owneruserid, modifieruserid
FROM application_form_item;

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
;

-- :name create-form! :insert
INSERT INTO application_form
(organization, title, modifierUserId, ownerUserId, visibility, endt)
VALUES
(:organization,
 :title,
 :user,
 :user,
 'public',
 /*~ (if (:endt params) */ :endt /*~*/ NULL /*~ ) ~*/
);

-- :name create-form-item! :insert
INSERT INTO application_form_item
(type, value, modifierUserId, ownerUserId, visibility)
VALUES
(CAST (:type as itemtype), :value, :user, :user, 'public');

-- :name link-form-item! :insert
INSERT INTO application_form_item_map
(formId, formItemId, modifierUserId, itemOrder, formItemOptional, maxlength)
VALUES
(:form, :item, :user, :itemorder, :optional,
/*~ (if (:maxlength params) */ :maxlength /*~*/ NULL /*~ ) ~*/
);

-- :name localize-form-item! :insert
INSERT INTO application_form_item_localization
(itemId, langCode, title, inputPrompt)
VALUES
(:item, :langcode, :title, :inputprompt);

-- :name create-form-item-option! :insert
INSERT INTO application_form_item_options
  (itemId, key, langCode, label, displayOrder)
VALUES (:itemId, :key, :langCode, :label, :displayOrder);

-- :name get-form-item-options :? :*
SELECT itemId, key, langCode, label, displayOrder
FROM application_form_item_options
WHERE itemId = :item;

-- :name end-form-item! :!
UPDATE application_form_item
SET endt = current_timestamp
WHERE id = :id;

-- :name create-application! :insert
INSERT INTO catalogue_item_application
(applicantUserId, wfid, start)
VALUES (:user, :wfid,
/*~ (if (:start params) */ :start /*~*/ now() /*~ ) ~*/
)
RETURNING id;

-- :name add-application-item! :insert
INSERT INTO catalogue_item_application_items
(catAppId, catItemId)
VALUES
(:application, :item)
RETURNING catAppId, catItemId;

-- :name get-applications :? :*
-- :doc
-- - Pass in no arguments to get all applications.
-- - Use {:id id} to get a specific application
-- - Use {:applicant user} to filter by applicant
SELECT
  app.id, app.applicantUserId, app.start, app.description, wf.id as wfid, wf.fnlround, wf.workflowBody::TEXT as workflow
FROM catalogue_item_application app
LEFT OUTER JOIN workflow wf ON app.wfid = wf.id
WHERE 1=1
/*~ (when (:id params) */
  AND app.id = :id
/*~ ) ~*/
/*~ (when (:applicant params) */
  AND app.applicantUserId = :applicant
/*~ ) ~*/
;

-- :name update-application-description! :!
UPDATE catalogue_item_application
SET description = :description
WHERE id = :id;

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
;

-- :name add-entitlement! :!
-- TODO remove resId from this table to make it normalized?
INSERT INTO entitlement (catAppId, userId, resId)
SELECT :application, :user, cat.resid
FROM catalogue_item_application_items ciai
LEFT OUTER JOIN catalogue_item_application app ON ciai.catAppId = app.id
LEFT OUTER JOIN catalogue_item cat ON ciai.catItemId = cat.id
WHERE ciai.catAppId = :application;

-- :name end-entitlement! :!
UPDATE entitlement
SET endt = current_timestamp
WHERE catAppId = :application;

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
;

-- :name save-field-value! :!
INSERT INTO application_text_values
(catAppId, modifierUserId, value, formMapId)
VALUES
(:application, :user, :value,
 (SELECT id FROM application_form_item_map
  WHERE formId = :form AND formItemId = :item))
ON CONFLICT (catAppId, formMapId)
DO UPDATE
SET (modifierUserId, value) = (:user, :value);

-- :name save-attachment! :!
INSERT INTO application_attachments
(catAppId, modifierUserId, filename, type, data, formMapId)
VALUES
(:application, :user, :filename, :type, :data,
 (SELECT id FROM application_form_item_map
  WHERE formId = :form AND formItemId = :item))
ON CONFLICT (catAppId, formMapId)
DO UPDATE
SET (modifierUserId, filename, type, data) = (:user, :filename, :type, :data);

-- :name remove-attachment! :!
DELETE FROM application_attachments
WHERE catAppId = :application
AND formmapid = (SELECT id FROM application_form_item_map
                 WHERE formId = :form AND formItemId = :item);

-- :name get-attachment :? :1
SELECT filename, type, data FROM application_attachments attachments
LEFT OUTER JOIN application_form_item_map itemmap ON attachments.formMapId = itemmap.id
WHERE attachments.catAppId = :application
  AND itemmap.formItemId = :item
  AND itemmap.formId = :form;

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
WHERE catappid = :catappid AND licid = :licid AND modifieruserid = :actoruserid;

-- :name get-application-license-approval :? :1
SELECT state FROM application_license_approval_values
WHERE catappid = :catappid AND licid = :licid AND modifieruserid = :actoruserid;

-- :name create-license! :insert
INSERT INTO license
(ownerUserId, modifierUserId, title, type, textcontent, attachmentId, endt)
VALUES
(:owneruserid, :modifieruserid, :title, :type::license_type, :textcontent,
/*~ (if (:attachmentId params) */ :attachmentId /*~*/ NULL /*~ ) ~*/,
/*~ (if (:endt params) */ :endt /*~*/ NULL /*~ ) ~*/
);

-- :name create-license-attachment! :insert
INSERT INTO license_attachment
(modifierUserId, filename, type, data)
VALUES
(:user, :filename, :type, :data);

-- :name remove-license-attachment! :!
DELETE FROM license_attachment WHERE id = :id;

-- :name get-license-attachment :? :1
SELECT filename, type, data FROM license_attachment
WHERE id = :attachmentId;

-- :name create-license-localization! :insert
INSERT INTO license_localization
(licid, langcode, title, textcontent, attachmentId)
VALUES
(:licid, :langcode, :title, :textcontent,
/*~ (if (:attachmentId params) */ :attachmentId /*~*/ NULL /*~ ) ~*/
);

-- :name create-workflow! :insert
INSERT INTO workflow
(organization, ownerUserId, modifierUserId, title, fnlround, endt, workflowBody)
VALUES
(:organization,
 :owneruserid,
 :modifieruserid,
 :title,
 :fnlround,
 /*~ (if (:endt params) */ :endt /*~*/ NULL /*~ ) ~*/,
 /*~ (if (:workflow params) */ :workflow::jsonb /*~*/ NULL /*~ ) ~*/
);

-- :name create-workflow-license! :insert
INSERT INTO workflow_licenses
(wfid, licid, round)
VALUES
(:wfid, :licid, :round);

-- TODO: consider renaming this to link-resource-license!
-- :name create-resource-license! :insert
INSERT INTO resource_licenses
(resid, licid)
VALUES
(:resid, :licid);

-- :name set-resource-license-validity! :insert
-- :doc set license expiration
UPDATE resource_licenses rl
SET start = :start, endt = :end
WHERE rl.licid = :licid;

-- :name set-workflow-license-validity! :insert
-- :doc set license expiration
UPDATE workflow_licenses wl
SET start = :start, endt = :end
WHERE wl.licid = :licid;

-- :name create-workflow-actor! :insert
INSERT INTO workflow_actors
(wfid, actoruserid, role, round)
VALUES
(:wfid, :actoruserid, CAST (:role as workflow_actor_role), :round);

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
;

-- :name get-workflow-actors :? :*
SELECT
  actoruserid, role, round
FROM workflow_actors
WHERE wfid = :wfid;

-- :name get-workflow :? :1
SELECT
  wf.id, wf.organization, wf.owneruserid, wf.modifieruserid, wf.title, wf.fnlround, wf.visibility, wf.start, wf.endt AS "end",
  wf.workflowBody::TEXT as workflow, wf.enabled, wf.archived,
  (SELECT json_agg(joined)
   FROM (SELECT *, (SELECT json_agg(licloc)
                    FROM license_localization licloc
                    WHERE licloc.licid = lic.id) AS localizations
         FROM workflow_licenses wflic
         JOIN license lic ON (wflic.licid = lic.id)
         WHERE wf.id = wflic.wfid) joined)::TEXT AS licenses
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
;

-- :name get-workflows :? :*
SELECT
  wf.id, wf.organization, wf.owneruserid, wf.modifieruserid, wf.title, wf.fnlround, wf.visibility, wf.start, wf.endt,
  wf.workflowBody::TEXT as workflow, wf.enabled, wf.archived
FROM workflow wf;

-- :name clear-field-value! :!
DELETE FROM application_text_values
WHERE catAppId = :application
  AND formMapId = (SELECT id FROM application_form_item_map
                   WHERE formId = :form AND formItemId = :item);

-- :name get-field-value :? :n
SELECT
  value
FROM application_text_values textvalues
LEFT OUTER JOIN application_form_item_map itemmap ON textvalues.formMapId = itemmap.id
WHERE textvalues.catAppId = :application
  AND itemmap.formItemId = :item
  AND itemmap.formId = :form;

-- :name get-licenses :? :*
-- :doc
-- - Gets application licenses by workflow and catalogue item ids
-- - :wfid workflow id for workflow licenses
-- - :items vector of catalogue item ids for resource licenses
SELECT lic.id, lic.title, lic.type, lic.textcontent, wl.start, wl.endt, lic.enabled, lic.archived
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licid
WHERE wl.wfid = :wfid
UNION
SELECT lic.id, lic.title, lic.type, lic.textcontent, rl.start, rl.endt, lic.enabled, lic.archived
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
WHERE item.id IN (:v*:items)
ORDER BY id;

-- :name get-resource-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent, rl.start, rl.endt, lic.enabled, lic.archived
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
WHERE rl.resid = :id;

-- :name get-all-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent, lic.start, lic.endt, lic.enabled, lic.archived, lic.attachmentid
FROM license lic;

-- :name get-license :? :1
SELECT lic.id, lic.title, lic.type, lic.textcontent, lic.start, lic.endt, lic.enabled, lic.archived, lic.attachmentid
, TRUE AS active -- TODO implement active and archiving
FROM license lic
WHERE lic.id = :id;

-- :name get-license-localizations :? :*
SELECT licid, langcode, title, textcontent, attachmentid
FROM license_localization;

-- :name get-roles :? :*
SELECT role
FROM roles
WHERE userId = :user;

-- :name add-role! :!
INSERT INTO roles (userId, role)
VALUES (:user, :role)
ON CONFLICT (userId, role)
DO NOTHING;

-- :name add-user! :!
INSERT INTO users (userId, userAttrs)
VALUES (:user, :userattrs::jsonb)
ON CONFLICT (userId)
DO UPDATE SET userAttrs = :userattrs::jsonb;

-- :name get-users :? :*
SELECT userId
FROM users;

-- :name get-user-attributes :? :1
SELECT userAttrs::TEXT
FROM users
WHERE userId = :user;

-- :name get-application-events :? :*
SELECT id, appId, userId, round, event, comment, eventData::TEXT, time
FROM application_event
WHERE 1=1
/*~ (when (:application params) */
  AND appId = :application
/*~ ) ~*/
ORDER BY id ASC;

-- :name get-application-events-since :? :*
SELECT id, eventdata::TEXT
FROM application_event
WHERE id > :id
ORDER BY id ASC;

-- :name add-application-event! :insert
INSERT INTO application_event (appId, userId, round, event, comment, eventData)
VALUES (:application, :user, :round, :event, :comment,
/*~ (if (:eventdata params) */ :eventdata::jsonb /*~*/ NULL /*~ ) ~*/
);

-- :name get-application-states :? :*
SELECT unnest(enum_range(NULL::application_state));

-- :name log-entitlement-post! :insert
INSERT INTO entitlement_post_log (payload, status)
VALUES (:payload::jsonb, :status);

-- :name add-api-key! :insert
INSERT INTO api_key (apiKey, comment)
VALUES (:apikey, :comment);

-- :name get-api-key :? :1
SELECT apiKey FROM api_key
WHERE apiKey = :apikey;

-- :name get-application-by-invitation-token :? :1
SELECT app.id
FROM catalogue_item_application app
JOIN application_event evt ON (app.id = evt.appid)
WHERE evt.eventdata->>'invitation/token' = :token

-- :name get-invitation-tokens :? :*
SELECT
evt.eventdata->>'event/type' AS event,
evt.eventdata->>'invitation/token' AS token,
evt.eventdata->>'event/actor' AS actor,
evt.eventdata->'application/member'->>'name' AS name,
evt.eventdata->'application/member'->>'email' AS email
FROM application_event evt
WHERE evt.eventdata->>'invitation/token' IS NOT NULL
/*~ (when (:appid params) */
AND appid = :appid
/*~ ) ~*/
