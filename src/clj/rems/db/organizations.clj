(ns rems.db.organizations
  (:require [com.rpl.specter :refer [ALL transform]]
            [medley.core :refer [assoc-some update-existing]]
            [rems.cache :as cache]
            [rems.common.util :refer [getx index-by]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.users]
            [rems.schema-base :as schema-base]
            [schema.core :as s]
            [schema.coerce :as coerce])
  (:import rems.DataException))

(s/defschema OrganizationRaw
  (merge schema-base/OrganizationOverview
         {(s/optional-key :organization/owners) [schema-base/User]
          (s/optional-key :organization/review-emails) [{:name schema-base/LocalizedString
                                                         :email s/Str}]
          (s/optional-key :enabled) s/Bool
          (s/optional-key :archived) s/Bool}))

(def ^:private validate-organization
  (s/validator OrganizationRaw))

(def ^:private coerce-organization-raw
  (coerce/coercer! OrganizationRaw json/coercion-matcher))

(def ^:private coerce-organization-full
  (coerce/coercer! schema-base/OrganizationFull json/coercion-matcher))

(defn- parse-organization-raw [x]
  (let [data (json/parse-string (:data x))
        org (-> {:organization/id (:id x)
                 :organization/short-name (:organization/short-name data)
                 :organization/name (:organization/name data)}
                (assoc-some :organization/owners (:organization/owners data)
                            :organization/review-emails (:organization/review-emails data)
                            :enabled (:enabled data)
                            :archived (:archived data)))]
    (coerce-organization-raw org)))

(def organization-cache
  (cache/basic {:id ::organization-cache
                :miss-fn (fn [id]
                           (if-let [organization (db/get-organization-by-id {:id id})]
                             (parse-organization-raw organization)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-organizations)
                                  (mapv parse-organization-raw)
                                  (index-by [:organization/id])))}))

(defn get-organizations-raw []
  (vals (cache/entries! organization-cache)))

(defn get-organization-by-id-raw [id]
  (cache/lookup-or-miss! organization-cache id))

(defn get-organizations []
  (->> (get-organizations-raw)
       (transform [ALL :organization/owners ALL] #(rems.db.users/get-user (:userid %)))
       (mapv coerce-organization-full)))

(defn- getx-organization-id [x]
  (let [id (if (map? x)
             (getx x :organization/id)
             x)]
    (assert (string? id) {:id id})
    id))

(defn getx-organization-by-id [x]
  (let [id (getx-organization-id x)
        org (get-organization-by-id-raw id)]
    (if-not org
      (throw (DataException. (str "organization \"" id "\" does not exist")
                             {:errors [{:type :t.actions.errors/organization-does-not-exist
                                        :args [id]
                                        :organization/id id}]}))
      (->> org
           (transform [:organization/owners ALL] rems.db.users/join-user)
           coerce-organization-full))))

(defn join-organization [x]
  ;; TODO alternatively we could pass in the organization key
  ;; TODO alternatively we could decide which layer transforms db string into {:organization/id "string"} and which layer joins the rest https://github.com/CSCfi/rems/issues/2179
  (let [organization-overview #(select-keys % [:organization/id :organization/name :organization/short-name])]
    (-> x
        (update-existing :organization #(-> % getx-organization-by-id organization-overview)))))

(defn add-organization! [org]
  (validate-organization org)
  (let [id (:id (db/add-organization! {:id (:organization/id org)
                                       :data (json/generate-string (-> org
                                                                       (assoc :enabled true
                                                                              :archived false)
                                                                       (dissoc :organization/id)))}))]
    (cache/miss! organization-cache id)
    id))

(defn set-organization! [organization]
  (let [id (:organization/id organization)
        stripped-organization (-> organization
                                  (update :organization/owners (partial mapv #(select-keys % [:userid])))
                                  validate-organization)]
    (db/set-organization! {:id id
                           :data (json/generate-string stripped-organization)})
    (cache/miss! organization-cache id)
    id))

(defn update-organization! [id update-fn]
  (let [organization (getx-organization-by-id (or (:organization/id id) id))]
    (set-organization! (update-fn organization))))

(defn get-all-organization-roles [userid]
  (when (some #(contains? (set (map :userid (:organization/owners %))) userid)
              (get-organizations-raw))
    #{:organization-owner}))
