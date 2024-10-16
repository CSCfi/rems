(ns rems.db.licenses
  (:require [clojure.set]
            [medley.core :refer [assoc-some map-vals]]
            [rems.cache :as cache]
            [rems.common.util :refer [apply-filters index-by]]
            [rems.db.core :as db]
            [schema.coerce :as coerce]
            [schema.core :as s]))

(s/defschema LicenseDb
  {:id s/Int
   :licensetype (s/enum "link" "text" "attachment")
   :organization s/Str
   :enabled s/Bool
   :archived s/Bool})

(def ^:private coerce-LicenseDb
  (coerce/coercer! LicenseDb coerce/string-coercion-matcher))

(defn- parse-license-raw [x]
  (let [license {:id (:id x)
                 :licensetype (:type x)
                 :organization (:organization x)
                 :enabled (:enabled x)
                 :archived (:archived x)}]
    (coerce-LicenseDb license)))

(def license-cache
  (cache/basic {:id ::license-cache
                :miss-fn (fn [id]
                           (if-let [license (db/get-license {:id id})]
                             (parse-license-raw license)
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-all-licenses)
                                  (map parse-license-raw)
                                  (index-by [:id])))}))

(s/defschema LicenseLocalizationDb
  {:licid s/Int
   :langcode s/Keyword
   :title s/Str
   :textcontent s/Str
   (s/optional-key :attachment-id) s/Int})

(def ^:private coerce-LicenseLocalizationDb
  (coerce/coercer! LicenseLocalizationDb coerce/string-coercion-matcher))

(defn- parse-localization-raw [x]
  (let [localization (-> {:licid (:licid x)
                          :langcode (keyword (:langcode x))
                          :title (:title x)
                          :textcontent (:textcontent x)}
                         (assoc-some :attachment-id (:attachmentid x)))]
    (coerce-LicenseLocalizationDb localization)))

(def license-localizations-cache
  (cache/basic {:id ::license-localizations-cache
                :miss-fn (fn [id]
                           (if-let [localizations (seq (db/get-license-localizations {:id id}))]
                             (->> localizations
                                  (mapv parse-localization-raw)
                                  (index-by [:langcode]))
                             cache/absent))
                :reload-fn (fn []
                             (->> (db/get-license-localizations)
                                  (mapv parse-localization-raw)
                                  (index-by [:licid :langcode])))}))

(defn localize-license [license]
  (let [localizations (->> (cache/lookup-or-miss! license-localizations-cache (:id license))
                           (map-vals #(select-keys % [:attachment-id :textcontent :title])))]
    (-> license
        (assoc :localizations (or localizations {})))))

(defn get-license
  "Get a single license by id"
  [id]
  (some-> (cache/lookup-or-miss! license-cache id)
          localize-license))

(defn get-licenses [& [filters]]
  (->> (vals (cache/entries! license-cache))
       (apply-filters filters)
       (mapv localize-license)))

(defn join-license [x]
  (-> (get-license (:license/id x))
      (dissoc :id)
      (merge x)))

(defn license-exists? [id]
  (cache/has? license-cache id))

(defn set-enabled! [id enabled?]
  (db/set-license-enabled! {:id id :enabled enabled?})
  (cache/miss! license-cache id))

(defn set-archived! [id archived?]
  (db/set-license-archived! {:id id :archived archived?})
  (cache/miss! license-cache id))

(defn create-license! [{:keys [license-type organization-id localizations]}]
  (let [id (:id (db/create-license! {:organization organization-id
                                     :type license-type}))]
    (doseq [[langcode localization] localizations]
      (db/create-license-localization! {:licid id
                                        :langcode (name langcode)
                                        :title (:title localization)
                                        :textcontent (:textcontent localization)
                                        :attachmentId (:attachment-id localization)}))
    (cache/miss! license-localizations-cache id)
    (cache/miss! license-cache id)
    id))

