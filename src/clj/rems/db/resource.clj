(ns rems.db.resource
  (:require [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema ResourceDb
  {:id s/Int
   :owneruserid s/Str
   :modifieruserid s/Str
   :organization schema-base/OrganizationId
   :resid s/Str
   :enabled s/Bool
   :archived s/Bool
   (s/optional-key :resource/duo) {(s/optional-key :duo/codes) [schema-base/DuoCode]}})

(def ^:private coerce-ResourceDb
  (coerce/coercer! ResourceDb coerce/string-coercion-matcher))

(def ^:private validate-ResourceDb
  (s/validator ResourceDb))

(defn- format-resource [resource]
  (let [resourcedata (json/parse-string (:resourcedata resource))]
    (-> resource
        (update :organization (fn [organization-id] {:organization/id organization-id}))
        (dissoc :resourcedata)
        (merge resourcedata))))

(defn get-resource [id]
  (when-let [resource (db/get-resource {:id id})]
    (-> resource
        format-resource
        coerce-ResourceDb)))

(defn get-resources [filters]
  (->> (db/get-resources)
       (db/apply-filters filters)
       (map format-resource)
       (map coerce-ResourceDb)))

(defn ext-id-exists? [ext-id]
  (some? (db/get-resource {:resid ext-id})))

(defn create-resource! [resource user-id]
  (let [data (when-let [duo (:resource/duo resource)]
               {:resource/duo {:duo/codes (for [code (:duo/codes duo)]
                                            (select-keys code [:id :restrictions]))}})
        id (:id (db/create-resource! {:resid (:resid resource)
                                      :organization (get-in resource [:organization :organization/id])
                                      :owneruserid user-id
                                      :modifieruserid user-id
                                      :resourcedata (json/generate-string data)}))]
    (doseq [licid (:licenses resource)]
      (db/create-resource-license! {:resid id
                                    :licid licid}))
    id))
