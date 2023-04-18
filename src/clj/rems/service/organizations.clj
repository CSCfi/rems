(ns rems.service.organizations
  (:require [clojure.set :as set]
            [medley.core :refer [assoc-some find-first]]
            [rems.auth.util]
            [rems.common.util :refer [apply-filters]]
            [rems.db.applications :as applications]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.service.dependencies :as dependencies]
            [rems.service.util]))

(defn- apply-user-permissions [userid organizations]
  (let [user-roles (set/union (roles/get-roles userid)
                              (organizations/get-all-organization-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (some? (some #{:owner :organization-owner :handler :reporter} user-roles))]
    (for [org organizations]
      (if (or (nil? userid) can-see-all?)
        org
        (dissoc org
                :organization/review-emails
                :organization/owners
                :enabled
                :archived)))))

(defn- owner-filter-match? [owner org]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:organization/owners org))) owner)))

(defn- organization-filters [userid owner organizations]
  (->> organizations
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (doall)))

(defn get-organizations [& [{:keys [userid owner enabled archived]}]]
  (->> (organizations/get-organizations)
       (apply-filters (assoc-some {}
                                  :enabled enabled
                                  :archived archived))
       (organization-filters userid owner)))

(defn get-organization [userid org]
  (->> (get-organizations {:userid userid})
       (find-first (comp #{(:organization/id org)} :organization/id))))

(defn add-organization! [cmd]
  (if-let [id (organizations/add-organization! cmd)]
    {:success true
     :organization/id id}
    {:success false
     :errors [{:type :t.actions.errors/duplicate-id
               :organization/id (:organization/id cmd)}]}))

(defn edit-organization! [cmd]
  (let [id (:organization/id cmd)]
    (rems.service.util/check-allowed-organization! cmd)
    (organizations/update-organization! id (fn [organization] (->> (dissoc cmd :organization/id)
                                                                   (merge organization))))
    {:success true
     :organization/id id}))

(defn set-organization-enabled! [{:keys [enabled] :as cmd}]
  (let [id (:organization/id cmd)]
    (organizations/update-organization! id (fn [organization] (assoc organization :enabled enabled)))
    {:success true}))

(defn set-organization-archived! [{:keys [archived] :as cmd}]
  (let [id (:organization/id cmd)]
    (or (dependencies/change-archive-status-error archived  {:organization/id id})
        (do
          (organizations/update-organization! id (fn [organization] (assoc organization :archived archived)))
          {:success true}))))

(defn get-available-owners [] (users/get-users))
