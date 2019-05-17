(ns rems.db.catalogue
  (:require [clojure.core.memoize :as memo]
            [rems.common-util :refer [index-by]]
            [rems.db.core :as db]))

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

(def cached (memo/ttl get-cache :ttl/threshold +localizations-cache-time-ms+))

(defn localize-catalogue-item
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

(defn create-catalogue-item! [command]
  (let [id (:id (db/create-catalogue-item! (select-keys command [:title :form :resid :wfid :enabled :archived])))]
    {:success (not (nil? id))
     :id id}))

(defn create-catalogue-item-localization! [command]
  (let [return {:success (not (nil? (:id (db/create-catalogue-item-localization! (select-keys command [:id :langcode :title])))))}]
    ;; Reset cache so that next call to get localizations will get this one.
    (memo/memo-clear! cached)
    return))

(defn update-catalogue-item! [command]
  ;; TODO disallow unarchiving catalogue item if its resource, form or licenses are archived
  (db/set-catalogue-item-state! (select-keys command [:id :enabled :archived]))
  {:success true})
