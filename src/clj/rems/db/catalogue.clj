(ns rems.db.catalogue
  (:require [clj-time.core :as time]
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

(defn- get-catalogue-item-localizations
  "Load catalogue item localizations from the database."
  [id]
  (->> (db/get-catalogue-item-localizations {:id id})
       (map #(update-in % [:langcode] keyword))
       (index-by [:langcode])))

(defn- localize-catalogue-item
  "Associates localisations into a catalogue item from
  the preloaded state."
  [item]
  (assoc item :localizations (get-catalogue-item-localizations (:id item))))

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

(defn get-expanded-catalogue-item [id]
  (get-localized-catalogue-item id {:expand-names? true :expand-resource-data? true}))

(defn create-catalogue-item! [command]
  (db/create-catalogue-item! command))

(defn set-catalogue-item-data! [command]
  (db/set-catalogue-item-data! (select-keys command [:id :catalogueitemdata])))

(defn set-catalogue-item-organization! [command]
  (db/set-catalogue-item-organization! (select-keys command [:id :organization])))

(defn upsert-catalogue-item-localization! [command]
  (db/upsert-catalogue-item-localization! (select-keys command [:id :langcode :title :infourl])))

(defn set-catalogue-item-enabled! [{:keys [id enabled]}]
    ;; Clear endt in case it has been set in the db. Otherwise we might
  ;; end up with an enabled item that's not active and can't be made
  ;; active via the UI.
  (db/set-catalogue-item-endt! {:id id :end nil})
  (db/set-catalogue-item-enabled! {:id id :enabled enabled}))

(defn set-catalogue-item-archived! [{:keys [id archived]}]
  (db/set-catalogue-item-archived! {:id id
                                    :archived archived}))
