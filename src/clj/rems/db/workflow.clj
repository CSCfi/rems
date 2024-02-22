(ns rems.db.workflow
  (:require [rems.common.application-util :as application-util]
            [rems.common.util :refer [apply-filters]]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s]
            [medley.core :refer [update-existing-in]]))

(s/defschema WorkflowBody
  {:type (apply s/enum application-util/workflow-types)
   :handlers [s/Str]
   (s/optional-key :forms) [{:form/id s/Num}]
   (s/optional-key :licenses) [s/Int]
   (s/optional-key :disable-commands) [schema-base/DisableCommandRule]
   (s/optional-key :voting) (s/maybe schema-base/WorkflowVoting)
   (s/optional-key :anonymize-handling) s/Bool
   (s/optional-key :processing-states) [schema-base/WorkflowProcessingState]})

(def ^:private coerce-workflow-body
  (coerce/coercer! WorkflowBody coerce/string-coercion-matcher))

(def ^:private validate-workflow-body
  (s/validator WorkflowBody))

(defn create-workflow! [{:keys [anonymize-handling
                                disable-commands
                                forms
                                handlers
                                licenses
                                organization
                                processing-states
                                type
                                title
                                voting]}]
  (let [body (cond-> {:type type
                      :handlers handlers
                      :forms forms
                      :licenses licenses}
               (seq disable-commands) (assoc :disable-commands disable-commands)
               voting (assoc :voting voting)
               anonymize-handling (assoc :anonymize-handling anonymize-handling)
               (seq processing-states) (assoc :processing-states processing-states))]
    (:id (db/create-workflow! {:organization (:organization/id organization)
                               :title title
                               :workflow (json/generate-string
                                          (validate-workflow-body body))}))))

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update :workflow #(coerce-workflow-body (json/parse-string %)))
      (update :organization (fn [id] {:organization/id id}))
      (update-in [:workflow :licenses] #(mapv (fn [id] {:license/id id}) %))
      (update-in [:workflow :handlers] #(mapv users/get-user %))))

(defn get-workflow [id]
  (when-let [wf (db/get-workflow {:wfid id})]
    (enrich-and-format-workflow wf)))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map enrich-and-format-workflow)
       (apply-filters filters)))

(defn get-all-workflow-roles [userid]
  (when (some #(contains? (set (map :userid (get-in % [:workflow :handlers]))) userid)
              (get-workflows nil))
    #{:handler}))

(defn- unrich-workflow [workflow]
  ;; TODO: keep handlers always in the same format, to avoid this conversion (we can ignore extra keys)
  (-> workflow
      (update-existing-in [:workflow :handlers] #(map :userid %))
      (update-existing-in [:workflow :licenses] #(map :license/id %))))

(defn edit-workflow! [{:keys [anonymize-handling
                              disable-commands
                              handlers
                              id
                              organization
                              processing-states
                              title
                              voting]}]
  (let [workflow (unrich-workflow (get-workflow id))
        workflow-body (cond-> (:workflow workflow)
                        handlers (assoc :handlers handlers)
                        disable-commands (assoc :disable-commands disable-commands)
                        voting (assoc :voting voting)
                        (some? anonymize-handling) (assoc :anonymize-handling anonymize-handling)
                        processing-states (assoc :processing-states processing-states))]
    (db/edit-workflow! {:id (or id (:id workflow))
                        :title (or title (:title workflow))
                        :organization (or (:organization/id organization)
                                          (get-in workflow [:organization :organization/id]))
                        :workflow (json/generate-string workflow-body)}))
  {:success true})
