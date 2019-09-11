(ns rems.migrations.missing-licenses-in-resources-changed
  (:require [hugsql.core :as hugsql]
            [rems.json :as json]))

;; Fixes the resources-changed type events in the database. Those events
;; did not have :application/licenses field previously, which caused the
;; licenses to not be updated when resources were changed.
;;
;; This migration goes through all resources-changed events in the database
;; and adds :application/licenses field to the event data, with license ids
;; corresponding to the new selection of resources.

;; SQL query for get-licenses repeated here so that this migration is standalone

(hugsql/def-db-fns-from-string
  "
-- :name get-events-of-type-resources-changed :? :*
SELECT id, appid, eventdata::TEXT
FROM application_event
WHERE eventdata->>'event/type' = 'application.event/resources-changed';

-- :name update-event! :!
UPDATE application_event
SET eventdata = eventdata || :value::jsonb
WHERE id = :id;

-- :name get-licenses :? :*
-- :doc
-- - Gets application licenses by workflow and catalogue item ids
-- - :wfid workflow id for workflow licenses
-- - :items vector of catalogue item ids for resource licenses
SELECT lic.id, lic.type, lic.enabled, lic.archived
FROM license lic
INNER JOIN workflow_licenses wl ON lic.id = wl.licid
WHERE wl.wfid = :wfid
UNION
SELECT lic.id, lic.type, lic.enabled, lic.archived
FROM license lic
INNER JOIN resource_licenses rl ON lic.id = rl.licid
INNER JOIN catalogue_item item ON (item.resid = rl.resid)
WHERE item.id IN (:v*:items)
ORDER BY id;

-- :name get-catalogue-item-workflow :? :1
SELECT wfid
FROM catalogue_item
WHERE id = :id;
")

(defn get-catalogue-item-license-ids [catalogue-item-id conn]
  (let [wfid (:wfid (get-catalogue-item-workflow conn {:id catalogue-item-id}))]
    (map :id (get-licenses conn {:wfid wfid
                                 :items [catalogue-item-id]}))))

(defn migrate-up [{:keys [conn]}]
  (doseq [{:keys [id appid eventdata]}
          (get-events-of-type-resources-changed conn)]
    (let [event (json/parse-string eventdata)
          cat-ids (mapv :catalogue-item/id (:application/resources event))
          licenses (->> cat-ids
                        (mapcat #(get-catalogue-item-license-ids % conn))
                        distinct)
          value (json/generate-string
                 {:application/licenses
                  (map (fn [licid] {:license/id licid}) licenses)})]
      (update-event! conn {:id id
                           :value value}))))
