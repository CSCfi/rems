(ns rems.db.organizations
  (:require [clojure.set :as set]
            [rems.api.schema :refer [Organization]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.json :as json]
            [schema.coerce :as coerce])
  (:import [org.joda.time DateTime]))

(defn add-organization! [userid org]
  (db/add-organization! {:id (:organization/id org)
                         :user userid
                         :time (DateTime.)
                         :data (json/generate-string org)})
  {:success true
   :organization/id (:organization/id org)})

(def ^:private coerce-organization
  (coerce/coercer! Organization json/coercion-matcher))

(defn- parse-organization [raw]
  (merge
   (json/parse-string (:data raw))
   {:organization/modifier {:userid (:modifieruserid raw)}
    :organization/last-modified (:modified raw)}))

(defn- apply-user-permissions [userid organizations]
  (let [user-roles (set/union (roles/get-roles userid)
                              (applications/get-all-application-roles userid))
        can-see-all? (seq (set/intersection user-roles #{:owner :organization-owner :handler}))]
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

(defn get-organizations [& [userid owner]]
  (->> (db/get-organizations)
       (map parse-organization)
       (map coerce-organization)
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
