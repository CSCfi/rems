(ns rems.db.catalogue
  (:require [clj-time.core :as time]
            [clojure.core.memoize :as memo]
            [rems.common.util :refer [index-by]]
            [rems.db.core :as db]
            [rems.json :as json]
            [rems.schema-base :as schema-base]
            [schema.core :as s]
            [schema.coerce :as coerce]))

(s/defschema CatalogueItemData
  (s/maybe {(s/optional-key :categories) [schema-base/CategoryId]}))

(def ^:private validate-catalogueitemdata
  (s/validator CatalogueItemData))

(def ^:private coerce-CatalogueItemData
  (coerce/coercer! CatalogueItemData coerce/string-coercion-matcher))

(def ^:private +localizations-cache-time-ms+ (* 5 60 1000))

(defn- load-catalogue-item-localizations!
  "Load catalogue item localizations from the database."
  []
  (->> (db/get-catalogue-item-localizations)
       (map #(update-in % [:langcode] keyword))
       (index-by [:id :langcode])))

(defn- get-cache [cache-key]
  (case cache-key
    :localizations (load-catalogue-item-localizations!)))

(def ^:private cached (memo/ttl get-cache :ttl/threshold +localizations-cache-time-ms+))

(defn- localize-catalogue-item
  "Associates localisations into a catalogue item from
  the preloaded state."
  [item]
  (assoc item :localizations (get (cached :localizations) (:id item) {})))

(defn- join-catalogue-item-data [item]
  (let [catalogueitemdata (json/parse-string (:catalogueitemdata item))]
    (-> (dissoc item :catalogueitemdata)
        (merge (coerce-CatalogueItemData catalogueitemdata)))))

(defn catalogueitemdata->json [data]
  (-> (select-keys data [:categories])
      (update :categories (fn [categories] (->> categories (mapv #(select-keys % [:category/id])))))
      validate-catalogueitemdata
      json/generate-string))

(defn now-active?
  ([start end]
   (now-active? (time/now) start end))
  ([now start end]
   (and (or (nil? start)
            (not (time/before? now start)))
        (or (nil? end)
            (time/before? now end)))))

(defn assoc-expired
  "Calculates and assocs :expired attribute based on current time and :start and :end attributes.

   Current time can be passed in optionally."
  ([x]
   (assoc-expired (time/now) x))
  ([now x]
   (assoc x :expired (not (now-active? now (:start x) (:end x))))))

(defn get-localized-catalogue-items
  ([]
   (get-localized-catalogue-items {}))
  ([query-params]
   (->> (db/get-catalogue-items query-params)
        (mapv localize-catalogue-item)
        (mapv assoc-expired)
        (mapv join-catalogue-item-data))))

(defn get-localized-catalogue-item
  ([id]
   (get-localized-catalogue-item id {:expand-names? true :expand-catalogue-data? true}))
  ([id query-params]
   (first (get-localized-catalogue-items (merge {:ids [id]
                                                 :archived true}
                                                query-params)))))

(defn reset-cache! []
  (memo/memo-clear! cached))
