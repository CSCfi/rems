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

(defn create-workflow! [{:keys [organization type title handlers forms]}]
  (let [body {:type type
              :handlers handlers
              :forms forms}]
    (:id (db/create-workflow! {:organization (:organization/id organization)
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

(defn get-all-workflow-roles [userid]
  (when (some #(contains? (set (map :userid (get-in % [:workflow :handlers]))) userid)
              (get-workflows nil))
    #{:handler}))

(defn- unrich-workflow [workflow]
  ;; TODO: service does this too
  ;; TODO: keep handlers always in the same format, to avoid this conversion (we can ignore extra keys)
  (if (get-in workflow [:workflow :handlers])
    (update-in workflow [:workflow :handlers] #(map :userid %))
    workflow))

(defn edit-workflow! [{:keys [id organization title handlers]}]
  (let [workflow (unrich-workflow (get-workflow id))
        workflow-body (cond-> (:workflow workflow)
                        handlers (assoc :handlers handlers))]
    (db/edit-workflow! {:id (or id (:id workflow))
                        :title (or title (:title workflow))
                        :organization (or (:organization/id organization) (get-in workflow [:organization :organization/id]))
                        :workflow (json/generate-string workflow-body)}))
  {:success true})
