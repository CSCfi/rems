(ns rems.db.resource
  (:require [clojure.set]
            [medley.core :refer [assoc-some]]
            [rems.cache :as cache]
            [rems.common.util :refer [apply-filters assoc-some-in index-by]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema ResourceDb
  {:id s/Int
   :organization schema-base/OrganizationId
   :resid s/Str
   :enabled s/Bool
   :archived s/Bool
   (s/optional-key :resource/duo) {(s/optional-key :duo/codes) [schema-base/DuoCode]}
   (s/optional-key :licenses) [schema-base/LicenseId]})

(def ^:private coerce-ResourceDb
  (coerce/coercer! ResourceDb coerce/string-coercion-matcher))

(defn- resourcedata->json [resource]
  (let [duos (->> (get-in resource [:resource/duo :duo/codes])
                  (map #(select-keys % [:id :restrictions :more-info])))
        resourcedata (assoc-some-in {}
                                    [:resource/duo :duo/codes] (seq duos))]
    (json/generate-string resourcedata)))

(defn- get-resource-licenses [x]
  (->> (db/get-resource-licenses {:id (:id x)})
       (map #(do {:license/id (:id %)}))))

(defn- parse-resource-raw [x]
  (let [data (json/parse-string (:resourcedata x))
        resource (-> {:id (:id x)
                      :organization {:organization/id (:organization x)}
                      :resid (:resid x)
                      :enabled (:enabled x)
                      :archived (:archived x)}
                     (assoc-some :resource/duo (:resource/duo data)
                                 :licenses (seq (get-resource-licenses x))))]
    (coerce-ResourceDb resource)))

(def resource-cache
  (cache/basic {:id ::resource-cache
                :miss-fn (fn [id]
                           (if-let [res (db/get-resource {:id id})]
                             (parse-resource-raw res)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-resources)
                                  (map parse-resource-raw)
                                  (index-by [:id])))}))

(defn get-resource [id]
  (cache/lookup-or-miss! resource-cache id))

(defn get-resources [& [filters]]
  (->> (vals (cache/entries! resource-cache))
       (into [] (apply-filters filters))))

(defn create-resource! [resource]
  (let [id (:id (db/create-resource! {:resid (:resid resource)
                                      :organization (get-in resource [:organization :organization/id])
                                      :resourcedata (resourcedata->json resource)}))]
    (doseq [licid (:licenses resource)]
      (db/create-resource-license! {:resid id
                                    :licid licid}))
    (cache/miss! resource-cache id)
    id))

;; XXX: unused function?
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

(defn set-enabled! [id enabled?]
  (db/set-resource-enabled! {:id id :enabled enabled?})
  (cache/miss! resource-cache id))

(defn set-archived! [id archived?]
  (db/set-resource-archived! {:id id :archived archived?})
  (cache/miss! resource-cache id))

(defn ext-id-exists? [ext-id]
  (some? (first (get-resources {:resid ext-id}))))
