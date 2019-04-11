(ns rems.db.dynamic-roles
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.application.model :as model]
            [rems.db.applications :as applications]
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

(mount/defstate roles-cache
  :start (atom {:last-processed-event-id 0
                :applications nil}))

(defn get-roles [user]
  (let [cache @roles-cache
        events (applications/get-dynamic-application-events-since (:last-processed-event-id cache))
        applications (reduce permissions-of-all-applications (:applications cache) events)]
    (when-let [event-id (:event/id (last events))]
      (when (compare-and-set! roles-cache
                              cache
                              {:last-processed-event-id event-id
                               :applications applications})
        (log/info "Updated roles-cache from" (:last-processed-event-id cache) "to" event-id)))
    (roles-of-user user applications)))
