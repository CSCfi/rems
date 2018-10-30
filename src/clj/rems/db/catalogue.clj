(ns rems.db.catalogue
  (:require [clojure.core.memoize :as memo]
            [rems.context :as context]
            [rems.db.core :as db]
            [rems.common-util :refer [index-by]]))

(def +localizations-cache-time-ms+ (* 5 60 1000))

(defn load-catalogue-item-localizations!
  "Load catalogue item localizations from the database."
  []
  (->> (db/get-catalogue-item-localizations)
       (map #(update-in % [:langcode] keyword))
       (index-by [:id :langcode])))

(defn get-cache [cache-key]
  (case cache-key
    :localizations (load-catalogue-item-localizations!)))

(def cached
  (memo/ttl get-cache :ttl/threshold +localizations-cache-time-ms+))

(defn localize-catalogue-item
  "Associates localisations into a catalogue item from
  the preloaded state."
  [item]
  (assoc item :localizations ((cached :localizations) (:id item))))

(defn get-localized-catalogue-items
  ([]
   (get-localized-catalogue-items {}))
  ([query-params]
   (map localize-catalogue-item (db/get-catalogue-items query-params))))

(defn get-localized-catalogue-item [id]
  (when-let [item (db/get-catalogue-item {:item id})]
    (localize-catalogue-item item)))

(defn get-catalogue-item-title [item]
  (let [localized-title (get-in item [:localizations context/*lang* :title])]
    (or localized-title (:title item))))

(defn disabled-catalogue-item? [item]
  (= (:state item) "disabled"))

(defn create-catalogue-item! [command]
  (let [id (:id (db/create-catalogue-item! (select-keys command [:title :form :resid :wfid])))]
    (get-localized-catalogue-item id)))

(defn create-catalogue-item-localization! [command]
  {:success (not (nil? (:id (db/create-catalogue-item-localization! (select-keys command [:id :langcode :title])))))})
