(ns rems.db.organizations
  (:require [medley.core :refer [update-existing]]
            [rems.api.schema :refer [OrganizationFull OrganizationOverview]]
            [rems.db.core :as db]
            [rems.json :as json]
            [schema.coerce :as coerce]
            [clj-time.core :as time-core])
  (:import [org.joda.time DateTime]
           rems.DataException))

(def ^:private +organizations-cache-time-ms+ (* 5 60 1000))

(defn add-organization! [userid org]
  (:id (db/add-organization! {:id (:organization/id org)
                              :user userid
                              :time (DateTime.)
                              :data (json/generate-string (-> org
                                                              (assoc :enabled true
                                                                     :archived false)
                                                              (dissoc :organization/id)))})))

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
    (when-not (:organization/id organization)
      (throw (DataException. (str "organization \"" id "\" does not exist") {:errors [{:type :t.actions.errors/organization-does-not-exist  :args [id] :organization/id id}]})))
    (coerce-organization-full organization)))

(defn join-organization [x]
  ;; TODO alternatively we could pass in the organization key
  ;; TODO alternatively we could decide which layer transforms db string into {:organization/id "string"} and which layer joins the rest https://github.com/CSCfi/rems/issues/2179
  (let [organization (:organization x)
        organization-id (if (string? organization) organization (:organization/id organization))
        organization-overview (-> organization-id
                                  getx-organization-by-id
                                  (select-keys [:organization/id :organization/name :organization/short-name]))]
    (-> x
        (update-existing :organization (fn [_] organization-overview))
        (update-existing :organization (fn [_] organization-overview)))))

(defn set-organization! [userid organization]
  (db/set-organization! {:id (:organization/id organization)
                         :data (json/generate-string organization)
                         :user userid
                         :time (time-core/now)}))

(defn update-organization! [userid id update-fn]
  (let [id (:organization/id id id)
        organization (getx-organization-by-id id)]
    (set-organization! userid (update-fn organization))))

(defn get-all-organization-roles [userid]
  (when (some #(contains? (set (map :userid (:organization/owners %))) userid)
              (get-organizations-raw))
    [:organization-owner]))
