(ns rems.api.services.organizations
  (:require [clojure.set :as set]
            [medley.core :refer [assoc-some find-first]]
            [rems.api.services.dependencies :as dependencies]
            [rems.api.services.util]
            [rems.auth.util]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]
            [rems.db.users :as users]))

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
       (db/apply-filters (assoc-some {}
                                     :enabled enabled
                                     :archived archived))
       (organization-filters userid owner)))

(defn get-organization-raw [org]
  (->> (organizations/get-organizations-raw)
       (find-first (comp #{(:organization/id org)} :organization/id))))

(defn get-organization [userid org]
  (->> (get-organizations {:userid userid})
       (find-first (comp #{(:organization/id org)} :organization/id))))

(defn add-organization! [org]
  (if-let [id (organizations/add-organization! org)]
    {:success true
     :organization/id id}
    {:success false
     :errors [{:type :t.actions.errors/duplicate-id
               :organization/id (:organization/id org)}]}))

(defn edit-organization! [{:organization/keys [id] :as org}]
  (rems.api.services.util/check-allowed-organization! org)
  (organizations/update-organization! id (fn [organization] (->> (dissoc org :organization/id)
                                                                 (merge organization))))
  {:success true
   :organization/id id})

(defn set-organization-enabled! [{:organization/keys [id] :keys [enabled]}]
  (organizations/update-organization! id (fn [organization] (assoc organization :enabled enabled)))
  {:success true})

(defn set-organization-archived! [{:organization/keys [id] :keys [archived]}]
  (or (dependencies/change-archive-status-error archived  {:organization/id id})
      (do
        (organizations/update-organization! id (fn [organization] (assoc organization :archived archived)))
        {:success true})))

(defn get-available-owners [] (users/get-users))
