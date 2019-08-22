-- :name get-catalogue-items :? :*
-- :doc
-- - Get catalogue items
-- - :ids vector of item ids
-- - :resource resource external id to fetch items for
-- - :resource-id resource internal id to fetch items for
-- - :workflow workflow id to fetch items for
-- - :form form id to fetch items for
-- - :archived true if archived items should be included
SELECT ci.id, res.resid, ci.wfid, ci.formid, ci.start, ci.endt as "end", ci.enabled, ci.archived
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
LEFT OUTER JOIN form_template form ON (ci.formid = form.id)
/*~ ) ~*/
WHERE 1=1
/*~ (when (:ids params) */
  AND ci.id IN (:v*:ids)
/*~ ) ~*/
/*~ (when (:resource params) */
  AND res.resid = :resource
/*~ ) ~*/
/*~ (when (:resource-id params) */
  AND ci.resid = :resource-id
/*~ ) ~*/
/*~ (when (:form params) */
  AND ci.formid = :form
/*~ ) ~*/
/*~ (when (:workflow params) */
  AND ci.wfid = :workflow
/*~ ) ~*/
/*~ (when (not (:archived params)) */
  AND ci.archived = false
/*~ ) ~*/
;

-- :name set-catalogue-item-state! :insert
-- TODO set modifieruserid?
UPDATE catalogue_item
SET
/*~ (when (boolean? (:enabled params)) */
  enabled = :enabled,
/*~ ) ~*/
/*~ (when (boolean? (:archived params)) */
  archived = :archived,
/*~ ) ~*/
/*~ (when (contains? params :end) */
  endt = :end,
/*~ ) ~*/
  id = id
WHERE id = :id;

-- :name create-catalogue-item! :insert
-- :doc Create a single catalogue item
INSERT INTO catalogue_item
(formid, resid, wfid, enabled, archived)
VALUES (:form, :resid, :wfid,
--~ (if (contains? params :enabled) ":enabled" "true")
,
--~ (if (contains? params :archived) ":archived" "false")
);

-- :name get-resources :? :*
SELECT
  id,
  owneruserid,
  modifieruserid,
  organization,
  resid,
  start,
  endt as "end",
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
  endt as "end",
  enabled,
  archived
FROM resource
WHERE 1=1
/*~ (when (:id params) */
  AND id = :id
/*~ ) ~*/
/*~ (when (:resid params) */
  AND resid = :resid
/*~ ) ~*/
;

-- :name create-resource! :insert
-- :doc Create a single resource
INSERT INTO resource
(resid, organization, ownerUserId, modifieruserid, endt)
VALUES (:resid, :organization, :owneruserid, :modifieruserid,
 /*~ (if (:end params) */ :end /*~*/ NULL /*~ ) ~*/
);

-- :name set-resource-state! :insert
-- TODO set modifieruserid?
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

-- :name get-form-templates :? :*
SELECT
  id,
  organization,
  title,
  start,
  endt as "end",
  fields::TEXT,
  enabled,
  archived
FROM form_template;

-- :name get-form-template :? :1
SELECT
  id,
  organization,
  title,
  start,
  endt as "end",
  fields::TEXT,
  enabled,
  archived
FROM form_template
WHERE id = :id;

-- :name save-form-template! :insert
INSERT INTO form_template
(organization, title, modifierUserId, ownerUserId, fields)
VALUES
(:organization,
 :title,
 :user,
 :user,
 :fields::jsonb
);

-- :name edit-form-template! :!
UPDATE form_template
SET (organization, title, modifierUserId, fields) =
(:organization,
 :title,
 :user,
 :fields::jsonb)
WHERE
id = :id;

-- :name set-form-template-state! :!
-- TODO set modifieruserid?
UPDATE form_template
SET (enabled, archived) = (:enabled, :archived)
WHERE
id = :id;

-- :name create-application! :insert
INSERT INTO catalogue_item_application (id)
VALUES (nextval('catalogue_item_application_id_seq'))
RETURNING id;

-- :name add-entitlement! :!
INSERT INTO entitlement (catAppId, userId, resId)
VALUES (:application, :user, :resource);

-- :name end-entitlements! :!
UPDATE entitlement
SET endt = current_timestamp
WHERE catAppId = :application
/*~ (when (:user params) */
  AND entitlement.userId = :user
/*~ ) ~*/
/*~ (when (:resource params) */
  AND entitlement.resId = :resource
/*~ ) ~*/
;

-- :name get-entitlements :?
-- :doc
-- Params:
--   :application -- application id to limit select to
--   :user -- user id to limit select to
--   :resource -- resid to limit select to
--   :is-active? -- entitlement is without end date
SELECT res.id AS resourceId, res.resId, catAppId, entitlement.userId, entitlement.start, entitlement.endt AS "end", users.userAttrs->>'mail' AS mail FROM entitlement
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
  AND res.id = :resource
/*~ ) ~*/
/*~ (when (:is-active? params) */
  AND entitlement.endt IS NULL
/*~ ) ~*/
;

-- :name save-attachment! :insert
INSERT INTO attachment
(appId, modifierUserId, filename, type, data)
VALUES
(:application, :user, :filename, :type, :data);

-- :name get-attachment :? :1
SELECT appid, filename, type, data FROM attachment
WHERE id = :id;

-- :name get-attachments-for-application :? :*
SELECT id, filename, type, modifierUserId FROM attachment
WHERE appid = :application-id;

-- :name create-license! :insert
INSERT INTO license
(ownerUserId, modifierUserId, title, type, textcontent, attachmentId, endt)
VALUES
(:owneruserid, :modifieruserid, :title, :type::license_type, :textcontent,
/*~ (if (:attachmentId params) */ :attachmentId /*~*/ NULL /*~ ) ~*/,
/*~ (if (:end params) */ :end /*~*/ NULL /*~ ) ~*/
);

-- :name set-license-state! :!
UPDATE license
SET (enabled, archived) = (:enabled, :archived)
WHERE id = :id;

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
(organization, ownerUserId, modifierUserId, title, endt, workflowBody)
VALUES
(:organization,
 :owneruserid,
 :modifieruserid,
 :title,
 /*~ (if (:end params) */ :end /*~*/ NULL /*~ ) ~*/,
 /*~ (if (:workflow params) */ :workflow::jsonb /*~*/ NULL /*~ ) ~*/
);

-- :name update-workflow! :!
UPDATE workflow
SET
  id = :id
  --~(when (contains? params :enabled) ", enabled = :enabled")
  --~(when (contains? params :archived) ", archived = :archived")
  --~(when (contains? params :title) ", title = :title")
  --~(when (contains? params :workflow) ", workflowBody = :workflow::jsonb")
WHERE id = :id;

-- :name create-workflow-license! :insert
INSERT INTO workflow_licenses
(wfid, licid)
VALUES
(:wfid, :licid);

-- TODO: consider renaming this to link-resource-license!
-- :name create-resource-license! :insert
INSERT INTO resource_licenses
(resid, licid)
VALUES
(:resid, :licid);

-- TODO: only used in test data; consider removing
-- :name set-resource-license-validity! :insert
-- :doc set license expiration
UPDATE resource_licenses rl
SET start = :start, endt = :end
WHERE rl.licid = :licid;

-- TODO: only used in test data; consider removing
-- :name set-workflow-license-validity! :insert
-- :doc set license expiration
UPDATE workflow_licenses wl
SET start = :start, endt = :end
WHERE wl.licid = :licid;

-- :name get-workflow-licenses :? :*
SELECT licid, start, endt as "end"
FROM workflow_licenses
WHERE wfid = :wfid

-- :name get-workflow :? :1
SELECT
  wf.id, wf.organization, wf.owneruserid, wf.modifieruserid, wf.title, wf.start, wf.endt AS "end",
  wf.workflowBody::TEXT as workflow, wf.enabled, wf.archived
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
  wf.id, wf.organization, wf.owneruserid, wf.modifieruserid, wf.title, wf.start, wf.endt as "end",
  wf.workflowBody::TEXT as workflow, wf.enabled, wf.archived
FROM workflow wf;

-- :name get-licenses :? :*
-- :doc
-- - Gets application licenses by workflow and catalogue item ids
-- - :wfid workflow id for workflow licenses
-- - :items vector of catalogue item ids for resource licenses
SELECT lic.id, lic.title, lic.type, lic.textcontent, wl.start, wl.endt as "end", lic.enabled, lic.archived
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licid
WHERE wl.wfid = :wfid
UNION
SELECT lic.id, lic.title, lic.type, lic.textcontent, rl.start, rl.endt as "end", lic.enabled, lic.archived
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
WHERE item.id IN (:v*:items)
ORDER BY id;

-- :name get-resource-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent, rl.start, rl.endt as "end", lic.enabled, lic.archived
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
WHERE rl.resid = :id;

-- :name get-all-licenses :? :*
SELECT lic.id, lic.title, lic.type, lic.textcontent, lic.start, lic.endt as "end", lic.enabled, lic.archived, lic.attachmentid
FROM license lic;

-- :name get-license :? :1
SELECT lic.id, lic.title, lic.type, lic.textcontent, lic.start, lic.endt as "end", lic.enabled, lic.archived, lic.attachmentid
FROM license lic
WHERE lic.id = :id;

-- :name get-license-localizations :? :*
SELECT licid, langcode, title, textcontent, attachmentid
FROM license_localization;

-- :name get-resources-for-license :? :*
SELECT resid FROM resource_licenses WHERE licid = :id;

-- :name get-workflows-for-license :? :*
SELECT wfid FROM workflow_licenses WHERE licid = :id;

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

-- :name update-user-settings! :!
INSERT INTO user_settings (userId, settings)
VALUES (:user, :settings::jsonb)
ON CONFLICT (userId)
DO UPDATE SET settings = :settings::jsonb;

-- :name get-users :? :*
SELECT userId
FROM users;

-- :name get-users-with-role :? :*
SELECT userid
FROM roles
WHERE role = :role;

-- :name get-user-attributes :? :1
SELECT userAttrs::TEXT
FROM users
WHERE userId = :user;

-- :name get-user-settings :? :1
SELECT settings::TEXT
from user_settings
where userId = :user;

-- :name get-application-events :? :*
SELECT id, eventdata::TEXT
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
INSERT INTO application_event (appId, eventData)
VALUES (:application, :eventdata::jsonb);

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
WHERE evt.eventdata->>'invitation/token' = :token;

-- :name get-poller-state :? :1
SELECT state::TEXT FROM poller_state
WHERE name = :name;

-- :name set-poller-state! :!
INSERT INTO poller_state (name, state)
VALUES (:name, :state::jsonb)
ON CONFLICT (name)
DO UPDATE
SET state = :state::jsonb;

-- :name get-external-ids :? :*
SELECT prefix, suffix FROM external_application_id
/*~ (when (:prefix params) */
WHERE prefix = :prefix
/*~ ) ~*/
;

-- :name add-external-id! :!
INSERT INTO external_application_id (prefix, suffix)
VALUES (:prefix, :suffix);

-- :name get-database-time :? :1
SELECT now();
