(ns rems.db.resource
  (:require [clojure.set]
            [rems.common.util :refer [apply-filters]]
            [rems.db.core :as db]
            [rems.ext.duo :as duo]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s])
  (:import [rems InvalidRequestException]))

(s/defschema ResourceDb
  {:id s/Int
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
  (->> (db/get-resources (select-keys filters [:resid]))
       (apply-filters (dissoc filters :resid)) ; other filters
       (map format-resource)
       (map coerce-ResourceDb)))

(defn ext-id-exists? [ext-id]
  (some? (db/get-resource {:resid ext-id})))

(defn create-resource! [resource]
  (let [missing-codes (clojure.set/difference (set (map :id (get-in resource [:resource/duo :duo/codes])))
                                              (set (map :id (duo/get-duo-codes))))]
    (when (seq missing-codes)
      (throw (InvalidRequestException. (str "Invalid DUO codes: " (pr-str missing-codes))))))
  (let [data (when-let [duo (:resource/duo resource)]
               {:resource/duo {:duo/codes (for [code (:duo/codes duo)]
                                            (select-keys code [:id :restrictions :more-info]))}})
        id (:id (db/create-resource! {:resid (:resid resource)
                                      :organization (get-in resource [:organization :organization/id])
                                      :resourcedata (json/generate-string data)}))]
    (doseq [licid (:licenses resource)]
      (db/create-resource-license! {:resid id
                                    :licid licid}))
    id))

(defn update-resource! [resource]
  (when-let [old-resource (get-resource (:id resource))]
    (let [amended (dissoc (merge old-resource
                                 resource)
                          :id)
          data (when-let [duo (:resource/duo amended)]
                 {:resource/duo {:duo/codes (for [code (:duo/codes duo)]
                                              (select-keys code [:id :restrictions :more-info]))}})]
      (db/update-resource! {:id (:id resource)
                            :resid (:resid amended)
                            :organization (get-in amended [:organization :organization/id])
                            :enabled (:enabled amended)
                            :archived (:archived amended)
                            :resourcedata (json/generate-string data)}))))
