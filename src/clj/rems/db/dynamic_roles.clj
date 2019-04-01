(ns rems.db.dynamic-roles
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [rems.application.model :as model]
            [rems.db.applications :as applications]
            [rems.permissions :as permissions]))

;; TODO: make private after removing the hack which uses this
(defn permissions-of-all-applications [applications event]
  (if-let [app-id (:application/id event)] ; old style events don't have :application/id
    (update applications app-id model/calculate-permissions event)
    applications))

(defn- roles-from-all-applications [user events]
  (->> (reduce permissions-of-all-applications nil events)
       vals
       (map #(permissions/user-roles % user))
       (apply set/union)))

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

(defn get-roles [user]
  ;; TODO: caching?
  (let [events (applications/get-dynamic-application-events-since 0)]
    (roles-from-all-applications user events)))
