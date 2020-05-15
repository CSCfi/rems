(ns rems.db.organizations
  (:require [clojure.core.memoize :as memo]
            [medley.core :refer [update-existing]]
            [rems.api.schema :refer [OrganizationFull OrganizationOverview]]
            [rems.common.util :refer [index-by]]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.json :as json]
            [schema.coerce :as coerce])
  (:import [org.joda.time DateTime]))

(def ^:private +organizations-cache-time-ms+ (* 5 60 1000))

(defn add-organization! [userid org]
  (db/add-organization! {:id (:organization/id org)
                         :user userid
                         :time (DateTime.)
                         :data (json/generate-string org)})
  {:success true
   :organization/id (:organization/id org)})

(def ^:private coerce-organization-overview
  (coerce/coercer! OrganizationOverview json/coercion-matcher))

(def ^:private coerce-organization-full
  (coerce/coercer! OrganizationFull json/coercion-matcher))

(defn- parse-organization [raw]
  (merge
   (json/parse-string (:data raw))
   {:organization/modifier {:userid (:modifieruserid raw)}
    :organization/last-modified (:modified raw)}))

(defn get-organizations-raw []
  (->> (db/get-organizations)
       (map parse-organization)
       (map coerce-organization-full)))

;; TODO unify caching behavior and location https://github.com/CSCfi/rems/issues/2179
(def ^:private organizations-by-id-cache (memo/ttl #(index-by [:organization/id] (get-organizations-raw)) :ttl/threshold +organizations-cache-time-ms+))

(defn get-organization-by-id-cached [id]
  (get (organizations-by-id-cache) id))

(defn join-full-organization [x]
  (update-existing x :organization (fn [id] (get-organization-by-id-cached id))))

(defn join-organization [x]
  ;; TODO alternatively we could pass in the organization key
  ;; TODO alternatively we could decide which layer transforms db string into {:organization/id "string"} and which layer joins the rest https://github.com/CSCfi/rems/issues/2179
  (let [organization (or (:form/organization x)
                         (:organization x))
        organization-id (if (string? organization) organization (:organization/id organization))
        organization-overview (-> organization-id
                                  get-organization-by-id-cached
                                  (select-keys [:organization/id :organization/name]))]
    (-> x
        (update-existing :organization (fn [_] organization-overview))
        (update-existing :form/organization (fn [_] organization-overview)))))
