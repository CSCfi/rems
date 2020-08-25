(ns rems.db.workflow
  (:require [rems.application.events :as events]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.util :refer [getx]]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema WorkflowBody
  {:type (apply s/enum events/workflow-types)
   :handlers [s/Str]
   (s/optional-key :forms) [{:form/id s/Num}]})

(def ^:private coerce-workflow-body
  (coerce/coercer! WorkflowBody coerce/string-coercion-matcher))

(def ^:private validate-workflow-body
  (s/validator WorkflowBody))

(defn create-workflow! [{:keys [user-id organization type title handlers forms]}]
  (let [body {:type type
              :handlers handlers
              :forms forms}]
    (:id (db/create-workflow! {:organization (:organization/id organization)
                               :owneruserid user-id
                               :modifieruserid user-id
                               :title title
                               :workflow (json/generate-string
                                          (validate-workflow-body body))}))))

(defn- get-workflow-licenses [id]
  (->> (db/get-workflow-licenses {:wfid id})
       (mapv #(licenses/get-license (getx % :licid)))))

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update :workflow #(coerce-workflow-body (json/parse-string %)))
      (update :organization (fn [id] {:organization/id id}))
      (assoc :licenses (get-workflow-licenses (:id wf)))
      (update-in [:workflow :handlers] #(mapv users/get-user %))))

(defn get-workflow [id]
  (when-let [wf (db/get-workflow {:wfid id})]
    (enrich-and-format-workflow wf)))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map enrich-and-format-workflow)
       (db/apply-filters filters)))

(defn join-workflow-licenses [workflow]
  (assoc workflow :licenses (get-workflow-licenses (:id workflow))))
