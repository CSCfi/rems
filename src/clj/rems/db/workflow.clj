(ns rems.db.workflow
  (:require [clojure.set]
            [medley.core :refer [assoc-some update-existing-in]]
            [rems.common.application-util :as application-util]
            [rems.cache :as cache]
            [rems.common.util :refer [index-by]]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema WorkflowBody
  {:type (apply s/enum application-util/workflow-types)
   :handlers [s/Str]
   (s/optional-key :forms) [{:form/id s/Num}]
   (s/optional-key :licenses) [s/Int]
   (s/optional-key :disable-commands) [schema-base/DisableCommandRule]
   (s/optional-key :voting) (s/maybe schema-base/WorkflowVoting)
   (s/optional-key :anonymize-handling) s/Bool
   (s/optional-key :processing-states) [schema-base/ProcessingState]})

(def ^:private coerce-workflow-body
  (coerce/coercer! WorkflowBody coerce/string-coercion-matcher))

(def ^:private validate-workflow-body
  (s/validator WorkflowBody))

(defn- parse-workflow-raw! [x]
  (-> x
      (update :workflow (comp coerce-workflow-body json/parse-string))
      (update :organization (fn [id] {:organization/id id}))
      (update-in [:workflow :licenses] (partial mapv (fn [id] {:license/id id})))
      (update-in [:workflow :handlers] (partial mapv (fn [id] {:userid id})))))

(def workflow-cache
  (cache/basic {:id ::workflow-cache
                :miss-fn (fn [id]
                           (-> (db/get-workflow {:wfid id})
                               parse-workflow-raw!))
                :reload-fn (fn []
                             (->> (db/get-workflows)
                                  (map parse-workflow-raw!)
                                  (index-by [:id])))}))

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
  (let [db-workflow {:organization (:organization/id organization)
                     :title title
                     :workflow (validate-workflow-body
                                (-> {:type type
                                     :handlers handlers
                                     :forms forms
                                     :licenses licenses}
                                    (assoc-some :anonymize-handling anonymize-handling
                                                :disable-commands (seq disable-commands)
                                                :processing-states (seq processing-states)
                                                :voting voting)))}
        id (:id (db/create-workflow! (update db-workflow :workflow json/generate-string)))]
    (cache/evict-and-miss! workflow-cache id)
    id))

(defn- enrich-workflow [wf]
  (update-existing-in wf [:workflow :handlers] (partial mapv users/join-user)))

(defn get-workflow [id]
  (-> (cache/lookup! workflow-cache id)
      enrich-workflow))

(defn get-workflows []
  (->> (vals (cache/entries! workflow-cache))
       (mapv enrich-workflow)))

(defn- get-handlers [wf]
  (set (map :userid (get-in wf [:workflow :handlers]))))

(defn get-all-workflow-roles [userid]
  (apply clojure.set/union
         (for [wf (get-workflows)]
           (set (concat (when (contains? (get-handlers wf) userid)
                          #{:handler}))))))

(defn edit-workflow! [{:keys [anonymize-handling
                              disable-commands
                              handlers
                              id
                              organization
                              processing-states
                              title
                              voting]}]
  (assert (some? id))
  (let [wf (cache/lookup! workflow-cache id)
        old-handlers (mapv :userid (get-in wf [:workflow :handlers]))
        old-licenses (mapv :license/id (get-in wf [:workflow :licenses]))
        new-wf {:id id
                :organization (or (:organization/id organization)
                                  (get-in wf [:organization :organization/id]))
                :title (or title (:title wf))
                :workflow (validate-workflow-body
                           (-> (:workflow wf)
                               ;; cache uses formatted values but db does not
                               (assoc :handlers (or handlers old-handlers)
                                      :licenses old-licenses)
                               (assoc-some :anonymize-handling anonymize-handling
                                           :disable-commands disable-commands
                                           :processing-states processing-states
                                           :voting voting)))}]
    (db/edit-workflow! (update new-wf :workflow json/generate-string))
    (cache/evict-and-miss! workflow-cache id)
    {:success true}))

(defn set-enabled! [id enabled?]
  (db/set-workflow-enabled! {:id id :enabled enabled?})
  (cache/evict-and-miss! workflow-cache id))

(defn set-archived! [id archived?]
  (db/set-workflow-archived! {:id id :archived archived?})
  (cache/evict-and-miss! workflow-cache id))
