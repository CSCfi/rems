(ns rems.db.catalogue
  (:require [clojure.core.memoize :as memo]
            [rems.common-util :refer [index-by]]
            [rems.db.core :as db]))

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
  (assoc item :localizations ((cached :localizations) (:id item))))

(defn get-localized-catalogue-items
  ([]
   (get-localized-catalogue-items {}))
  ([query-params]
   (->> (db/get-catalogue-items query-params)
        (map localize-catalogue-item)
        (map db/assoc-expired))))

(defn get-localized-catalogue-item [id]
  (first (get-localized-catalogue-items {:ids [id] :archived true :expand-names? true})))

(defn reset-cache! []
  (memo/memo-clear! cached))
