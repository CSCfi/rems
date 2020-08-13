(ns rems.db.organizations
  (:require [medley.core :refer [update-existing]]
            [rems.api.schema :refer [OrganizationFull OrganizationOverview]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce])
  (:import [org.joda.time DateTime]))

(def ^:private +organizations-cache-time-ms+ (* 5 60 1000))

(defn add-organization! [userid org]
  (db/add-organization! {:id (:organization/id org)
                         :user userid
                         :time (DateTime.)
                         :data (json/generate-string (-> org
                                                         (assoc :enabled true
                                                                :archived false)
                                                         (dissoc :organization/id)))})
  {:success true
   :organization/id (:organization/id org)})

(def ^:private coerce-organization-overview
  (coerce/coercer! OrganizationOverview json/coercion-matcher))

(def ^:private coerce-organization-full
  (coerce/coercer! OrganizationFull json/coercion-matcher))

(defn- parse-organization [raw]
  (merge
   (json/parse-string (:data raw))
   {:organization/id (:id raw)
    :organization/modifier {:userid (:modifieruserid raw)}
    :organization/last-modified (:modified raw)}))

(defn get-organizations-raw []
  (->> (db/get-organizations)
       (map parse-organization)
       (map coerce-organization-full)))

(defn getx-organization-by-id [id]
  (assert id)
  (let [organization (-> (db/get-organization-by-id {:id id}) parse-organization)]
    (assert (:organization/id organization) {:error "organization does not exist" :organization/id id :found organization})
    (coerce-organization-full organization)))

(defn join-organization [x]
  ;; TODO alternatively we could pass in the organization key
  ;; TODO alternatively we could decide which layer transforms db string into {:organization/id "string"} and which layer joins the rest https://github.com/CSCfi/rems/issues/2179
  (let [organization (:organization x)
        organization-id (if (string? organization) organization (:organization/id organization))
        organization-overview (-> organization-id
                                  getx-organization-by-id
                                  (select-keys [:organization/id :organization/name]))]
    (-> x
        (update-existing :organization (fn [_] organization-overview))
        (update-existing :organization (fn [_] organization-overview)))))

(defn set-organization! [organization]
  (db/set-organization! {:id (:organization/id organization) :data (json/generate-string organization)}))

(defn update-organization! [id update-fn]
  (let [id (:organization/id id id)
        organization (getx-organization-by-id id)]
    (set-organization! (update-fn organization))))
