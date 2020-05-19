(ns rems.api.services.organizations
  (:require [clojure.set :as set]
            [rems.db.applications :as applications]
            [rems.db.organizations :as organizations]
            [rems.db.roles :as roles]))

(defn- apply-user-permissions [userid organizations]
  (let [user-roles (set/union (roles/get-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (some? (some #{:owner :organization-owner :handler} user-roles))]
    (for [org organizations]
      (if (or (nil? userid) can-see-all?)
        org
        (dissoc org
                :organization/last-modified
                :organization/modifier
                :organization/review-emails
                :organization/owners)))))

(defn- owner-filter-match? [owner org]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:organization/owners org))) owner)))

(defn- organization-filters [userid owner organizations]
  (->> organizations
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (doall)))

(defn get-organizations [& [userid owner]]
  (->> (organizations/get-organizations-raw)
       (organization-filters userid owner)))

(defn add-organization! [userid org]
  (organizations/add-organization! userid org))
