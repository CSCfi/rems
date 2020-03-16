(ns rems.migrations.multiple-forms
  (:require [hugsql.core :as hugsql]
            [medley.core :refer [map-keys]]
            [rems.json :as json]
            [rems.util :refer [getx update-present]]))

(hugsql/def-db-fns-from-string
  "
-- :name get-application-events :? :*
SELECT id, eventdata::TEXT
FROM application_event
WHERE 1=1
/*~ (when (:application params) */
  AND appId = :application
/*~ ) ~*/
ORDER BY id ASC;

-- :name set-event! :!
UPDATE application_event
SET eventdata = (:eventdata::jsonb)
WHERE id = :id;
")

(defn- get-application-forms [conn]
  (reduce (fn [m {:keys [id eventdata]}]
            (let [event (json/parse-string eventdata)]
              (if (and (= "application.event/created" (:event/type event))
                       (:form/id event))
                (assoc m (:application/id event) (:form/id event))
                m)))
          {}
          (get-application-events conn)))

(defmulti migrate-event (fn [id event application-forms] (:event/type event)))

(defmethod migrate-event :default [id event application-forms]
  nil)

(defmethod migrate-event "application.event/draft-saved"
  [id event application-forms]
  (when (map? (:application/field-values event))
    (let [form-id (getx application-forms (:application/id event))]
      (update event
              :application/field-values
              (fn [m]
                (vec (for [[field-id field-value] m]
                       {:form form-id :field field-id :value field-value})))))))

(defmethod migrate-event "application.event/created"
  [id event _application-forms]
  (when (:form/id event)
    (-> event
        (dissoc :form/id)
        (assoc :application/forms [{:form/id (:form/id event)}]))))

(defn- migrate-events [conn application-forms]
  (doseq [{:keys [id eventdata]} (get-application-events conn)]
    (let [event (json/parse-string eventdata)
          new-event (migrate-event id event application-forms)]
      (when new-event
        (set-event! conn {:id id :eventdata (json/generate-string new-event)})))))

(defn migrate-up [{:keys [conn]}]
  (let [application-forms (get-application-forms conn)]
    (migrate-events conn application-forms)))

(comment
  (migrate-up {:conn rems.db.core/*db*}))
