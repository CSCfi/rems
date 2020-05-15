(ns rems.migrations.multiple-organizations
  (:require [hugsql.core :as hugsql]
            [medley.core :refer [map-keys]]
            [rems.json :as json]
            [rems.util :refer [getx update-present]]))

(hugsql/def-db-fns-from-string
  "
-- :name get-form-templates :? :*
SELECT
  id,
  organization,
  title,
  fields::TEXT,
  enabled,
  archived
FROM form_template;

-- :name update-organization! :!
UPDATE form_template SET organization :organization WHERE id = :id;
")

#_(defn- get-application-forms [conn]
  (->> (get-application-events conn)
       (map #(json/parse-string (:eventdata %)))
       (filter #(and (= "application.event/created" (:event/type %)) (:form/id %)))
       (map (juxt :application/id :form/id))
       (into {})))

#_(defmulti migrate-event (fn [id event application-forms] (:event/type event)))

#_(defmethod migrate-event :default [id event application-forms]
  nil)

#_(defmethod migrate-event "application.event/draft-saved"
  [id event application-forms]
  (when (map? (:application/field-values event))
    (let [form-id (getx application-forms (:application/id event))]
      (update event
              :application/field-values
              (fn [m]
                (vec (for [[field-id field-value] m]
                       {:form form-id :field field-id :value field-value})))))))

#_(defmethod migrate-event "application.event/created"
  [id event _application-forms]
  (when (:form/id event)
    (-> event
        (dissoc :form/id)
        (assoc :application/forms [{:form/id (:form/id event)}]))))

#_(defmethod migrate-event "application.event/resources-changed"
  [id event application-forms]
  (when-not (:application/forms event)
    (let [form-id (getx application-forms (:application/id event))]
      (assoc event :application/forms [{:form/id form-id}]))))

#_(defn- migrate-events [conn application-forms]
  (doseq [{:keys [id eventdata]} (get-application-events conn)]
    (let [event (json/parse-string eventdata)
          new-event (migrate-event id event application-forms)]
      (when new-event
        (set-event! conn {:id id :eventdata (json/generate-string new-event)})))))


(defn migrate-form-templates [conn templates]
  (doseq [template templates]
    (when (string? (:organization template))
      (update-organization! (:id template) {:organization/id (:organization template)}))))

(defn migrate-up [{:keys [conn]}]
  (migrate-form-templates conn (get-form-templates conn)))

(comment
  (migrate-up {:conn rems.db.core/*db*}))
