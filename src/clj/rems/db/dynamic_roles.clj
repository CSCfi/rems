(ns rems.db.dynamic-roles
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [mount.core :as mount]
            [rems.application.events-cache :as events-cache]
            [rems.application.model :as model]
            [rems.permissions :as permissions]))

(defn- permissions-of-all-applications [applications event]
  (if-let [app-id (:application/id event)] ; old style events don't have :application/id
    (update applications app-id model/calculate-permissions event)
    applications))

(defn- roles-of-user [user applications-by-id]
  (->> applications-by-id
       (vals)
       (map #(permissions/user-roles % user))
       (apply set/union)))

(defn- roles-from-all-applications [user events]
  (->> (reduce permissions-of-all-applications nil events)
       (roles-of-user user)))

(deftest test-roles-from-all-applications
  (let [events [{:event/type :application.event/created
                 :event/actor "applicant-only"
                 :application/id 1
                 :workflow.dynamic/handlers ["applicant-and-handler"]}
                {:event/type :application.event/created
                 :event/actor "applicant-and-handler"
                 :application/id 2}]]
    (is (= #{:applicant :everyone-else}
           (roles-from-all-applications "applicant-only" events)))
    (is (= #{:applicant :handler}
           (roles-from-all-applications "applicant-and-handler" events)))))

(mount/defstate dynamic-roles-cache
  :start (events-cache/new))

(defn get-roles [user]
  (->> (events-cache/refresh! dynamic-roles-cache
                              (fn [applications events]
                                (reduce permissions-of-all-applications applications events)))
       (roles-of-user user)))
