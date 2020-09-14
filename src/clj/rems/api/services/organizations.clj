(ns rems.api.services.organizations
  (:require [clojure.set :as set]
            [medley.core :refer [assoc-some find-first remove-keys]]
            [rems.api.services.dependencies :as dependencies]
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
                :organization/last-modified
                :organization/modifier
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
  (->> (organizations/get-organizations-raw)
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

(defn add-organization! [userid org]
  (if-let [id (organizations/add-organization! userid org)]
    {:success true
     :organization/id id}
    {:success false
     :errors [{:type :t.actions.errors/duplicate-id
               :organization/id (:organization/id org)}]}))

(defn edit-organization! [userid org]
  (organizations/update-organization! userid
                                      (:organization/id org)
                                      (fn [db-organization]
                                        (let [organization-owners (set (map :userid (:organization/owners db-organization)))
                                              organization-owner? (contains? organization-owners userid)]
                                          (merge db-organization
                                                 (remove-keys (if organization-owner?
                                                                #{:organization/id :organization/owners} ; org owner can't update owners
                                                                #{:organization/id})
                                                              org)))))
  {:success true
   :organization/id (:organization/id org)})

(defn set-organization-enabled! [userid {:organization/keys [id] :keys [enabled]}]
  (organizations/update-organization! userid id (fn [organization] (assoc organization :enabled enabled)))
  {:success true})

(defn set-organization-archived! [userid {:organization/keys [id] :keys [archived]}]
  (or (dependencies/change-archive-status-error archived  {:organization/id id})
      (do
        (organizations/update-organization! userid id (fn [organization] (assoc organization :archived archived)))
        {:success true})))

(defn get-available-owners [] (users/get-users))
