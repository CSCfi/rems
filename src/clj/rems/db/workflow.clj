(ns rems.db.workflow
  (:require [clj-time.core :as time-core]
            [clojure.test :refer [deftest is]]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.json :as json]
            [rems.util :refer [update-present]])
  (:import [org.joda.time DateTime DateTimeZone]))

(defn- parse-db-time [s]
  (when s
    (-> (DateTime/parse s)
        (.withZone DateTimeZone/UTC))))

(deftest test-parse-db-time
  (is (= nil (parse-db-time nil)))
  (is (= (time-core/date-time 2019 1 30 7 56 38 627) (parse-db-time "2019-01-30T09:56:38.627616+02:00")))
  (is (= (time-core/date-time 2015 2 13 12 47 26) (parse-db-time "2015-02-13T14:47:26+02:00"))))

;; TODO: This should probably be in rems.db.licenses.
(defn- format-license [license]
  (-> license
      (select-keys [:type :textcontent :localizations])
      (assoc :start (parse-db-time (:start license)))
      (assoc :end (parse-db-time (:end license)))))

(defn- format-workflow
  [{:keys [id organization owneruserid modifieruserid title workflow start end expired enabled archived licenses]}]
  {:id id
   :organization organization
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :title title
   :workflow workflow
   :start start
   :end end
   :expired expired
   :enabled enabled
   :archived archived
   :licenses (mapv format-license licenses)})

(defn- enrich-and-format-workflow [wf]
  (-> wf
      (update-present :workflow update :handlers #(mapv users/get-user %))
      format-workflow))

(defn- parse-workflow-body [json]
  (json/parse-string json))

(defn- parse-licenses [json]
  (json/parse-string json))

(defn get-workflow [id]
  (-> {:wfid id}
      db/get-workflow
      (update :workflow parse-workflow-body)
      (update :licenses parse-licenses)
      db/assoc-expired
      enrich-and-format-workflow))

(defn get-workflows [filters]
  (->> (db/get-workflows)
       (map #(update % :workflow parse-workflow-body))
       (map db/assoc-expired)
       (db/apply-filters filters)
       (mapv enrich-and-format-workflow)))
