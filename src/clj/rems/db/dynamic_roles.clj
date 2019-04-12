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

(defn- group-roles-by-user [applications-by-id]
  (->> applications-by-id
       (vals)
       (mapcat (fn [app] (::permissions/user-roles app)))
       (reduce (fn [roles-by-user [user roles]]
                 (update roles-by-user user set/union roles))
               {})))

(defn- get-user-roles [user roles-by-user]
  (get roles-by-user user #{}))

(defn- roles-from-all-applications [user events]
  (->> (reduce permissions-of-all-applications nil events)
       (group-roles-by-user)
       (get-user-roles user)))

(deftest test-roles-from-all-applications
  (let [events [{:event/type :application.event/created
                 :event/actor "applicant-only"
                 :application/id 1
                 :workflow.dynamic/handlers ["applicant-and-handler"]}
                {:event/type :application.event/created
                 :event/actor "applicant-and-handler"
                 :application/id 2}]]
    (is (= #{:applicant}
           (roles-from-all-applications "applicant-only" events)))
    (is (= #{:applicant :handler}
           (roles-from-all-applications "applicant-and-handler" events)))
    (is (= #{}
           (roles-from-all-applications "unknown" events)))))

(mount/defstate dynamic-roles-cache
  :start (events-cache/new))

(defn get-roles [user]
  (->> (events-cache/refresh!
        dynamic-roles-cache
        (fn [state events]
          (let [apps (reduce permissions-of-all-applications (::apps state) events)]
            {::apps apps
             ::roles-by-user (group-roles-by-user apps)})))
       ::roles-by-user
       (get-user-roles user)))
