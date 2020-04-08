(ns rems.db.organizations
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.json :as json]
            [rems.context :as context]))

(defn add-organization! [org]
  (db/add-organization! {:id (:organization/id org)
                         :data (json/generate-string org)})
  {:success true
   :organization/id (:organization/id org)})

(defn- parse-organization [raw]
  (json/parse-string (:data raw)))

(defn- apply-user-permissions [userid organizations]
  (let [user-roles (set/union (roles/get-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (seq (set/intersection user-roles #{:owner :organization-owner :handler}))]
    (for [org organizations]
      (if (or (nil? userid) can-see-all?)
        org
        (dissoc org
                :organization/review-emails
                :organization/owners)))))

(defn- owner-filter-match? [owner org]
  (or (nil? owner) ; return all when not specified
      (contains? (roles/get-roles owner) :owner) ; implicitly owns all
      (contains? (set (map :userid (:organization/owners org))) owner)))

(defn get-organizations [& [userid owner]]
  (->> (db/get-organizations)
       (map parse-organization)
       (apply-user-permissions userid)
       (filter (partial owner-filter-match? owner))
       (doall)))

(comment
  (get-organizations "owner")
  (get-organizations "owner" "organization-owner1")
  (get-organizations "owner" "alice")
  (get-organizations "alice")


  (set/intersection (set/union (roles/get-roles "alice")
                               (applications/get-all-application-roles "alice"))
                    #{:owner :organization-owner :handler}))
