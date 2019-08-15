(ns rems.db.workflow
  (:require [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.util :refer [update-present]]))

;; XXX: Overwriting :start and :end from license table with :start and :end
;;      from workflow_license table seems error-prone - they could at least
;;      be named differently to avoid confusion.
;;
;;      See a related comment in rems.db.licenses regarding the use of
;;      various start and end times.
(defn- join-workflow-license-with-license [workflow-license]
  (-> (:licid workflow-license)
      licenses/get-license
      (assoc :start (:start workflow-license)
             :end (:end workflow-license))))

(defn- get-workflow-licenses [id]
  (->> {:wfid id}
       db/get-workflow-licenses
       (mapv join-workflow-license-with-license)))

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update :workflow #(json/parse-string %))
      (assoc :licenses (get-workflow-licenses (:id wf)))
      db/assoc-expired
      (update-present :workflow update :handlers #(mapv users/get-user %))))

(defn get-workflow [id]
  (-> {:wfid id}
      db/get-workflow
      enrich-and-format-workflow))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map enrich-and-format-workflow)
       (db/apply-filters filters)))
