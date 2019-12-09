(ns rems.db.workflow
  (:require [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.util :refer [getx]]))

(defn- get-workflow-licenses [id]
  (->> (db/get-workflow-licenses {:wfid id})
       (mapv #(licenses/get-license (getx % :licid)))))

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update :workflow json/parse-string)
      (assoc :licenses (get-workflow-licenses (:id wf)))
      (update-in [:workflow :type] keyword)
      (update-in [:workflow :handlers] #(mapv users/get-user %))))

(defn get-workflow [id]
  (when-let [wf (db/get-workflow {:wfid id})]
    (enrich-and-format-workflow wf)))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map enrich-and-format-workflow)
       (db/apply-filters filters)))
