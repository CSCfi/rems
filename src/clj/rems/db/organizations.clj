(ns rems.db.organizations
  (:require [medley.core :refer [update-existing]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.db.users :as users]
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

(defn add-organization! [org]
  (validate-organization org)
  (:id (db/add-organization! {:id (:organization/id org)
                              :data (json/generate-string (-> org
                                                              (assoc :enabled true
                                                                     :archived false)
                                                              (dissoc :organization/id)))})))

(def ^:private coerce-organization-raw
  (coerce/coercer! OrganizationRaw json/coercion-matcher))

(def ^:private coerce-organization-full
  (coerce/coercer! schema-base/OrganizationFull json/coercion-matcher))

(defn- parse-organization [raw]
  (merge
   (json/parse-string (:data raw))
   {:organization/id (:id raw)}))

(defn get-organizations-raw []
  (->> (db/get-organizations)
       (mapv parse-organization)
       (mapv coerce-organization-raw)))

(defn get-organization-by-id-raw [id]
  (when-some [organization (db/get-organization-by-id {:id id})]
    (-> organization
        (parse-organization)
        (coerce-organization-raw))))

(defn get-organizations []
  (->> (get-organizations-raw)
       (mapv #(update % :organization/owners (partial mapv (comp users/get-user :userid))))
       (mapv coerce-organization-full)))

(defn getx-organization-by-id [id]
  (assert id)
  (let [organization (-> (db/get-organization-by-id {:id id})
                         parse-organization
                         (update :organization/owners (partial mapv (comp users/get-user :userid))))]
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

(defn set-organization! [organization]
  (let [stripped-organization (-> organization
                                  (update :organization/owners (partial mapv #(select-keys % [:userid])))
                                  validate-organization)]
    (db/set-organization! {:id (:organization/id organization)
                           :data (json/generate-string stripped-organization)})))

(defn update-organization! [id update-fn]
  (let [id (:organization/id id id)
        organization (getx-organization-by-id id)]
    (set-organization! (update-fn organization))))

(defn get-all-organization-roles [userid]
  (when (some #(contains? (set (map :userid (:organization/owners %))) userid)
              (get-organizations-raw))
    #{:organization-owner}))
