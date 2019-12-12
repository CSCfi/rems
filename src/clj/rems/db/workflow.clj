(ns rems.db.workflow
  (:require [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.util :refer [getx]]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(def workflow-types
  #{:workflow/decider
    :workflow/default
    :workflow/master})

(s/defschema WorkflowBody
  {:type (apply s/enum workflow-types)
   :handlers [s/Str]})

(def ^:private workflow-body-coercer
  (coerce/coercer WorkflowBody coerce/string-coercion-matcher))

(defn- coerce-workflow-body [body]
  (let [result (workflow-body-coercer body)]
    (if (schema.utils/error? result)
      (throw (ex-info "Failed to coerce workflow body" {:body body :error result}))
      result)))

(defn create-workflow! [{:keys [user-id organization type title handlers]}]
  (let [body {:type type
              :handlers handlers}]
    (s/validate WorkflowBody body)
    (:id (db/create-workflow! {:organization organization,
                               :owneruserid user-id,
                               :modifieruserid user-id,
                               :title title,
                               :workflow (json/generate-string body)}))))

(defn- get-workflow-licenses [id]
  (->> (db/get-workflow-licenses {:wfid id})
       (mapv #(licenses/get-license (getx % :licid)))))

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update :workflow #(coerce-workflow-body (json/parse-string %)))
      (assoc :licenses (get-workflow-licenses (:id wf)))
      (update-in [:workflow :handlers] #(mapv users/get-user %))))

(defn get-workflow [id]
  (when-let [wf (db/get-workflow {:wfid id})]
    (enrich-and-format-workflow wf)))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map enrich-and-format-workflow)
       (db/apply-filters filters)))
