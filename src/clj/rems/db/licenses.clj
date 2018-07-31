(ns rems.db.licenses
  "querying localized licenses"
  (:require [clj-time.core :as time]
            [rems.db.core :as db]
            [rems.util :refer [distinct-by]]))

(defn- format-licenses [licenses]
  (doall
   (for [license licenses]
     {:id (:id license)
      :licensetype (:type license)
      :start (:start license)
      :end (:endt license)
      ;; TODO why do licenses have a non-localized title & content while items don't?
      :title (:title license)
      :textcontent (:textcontent license)})))

(defn- localize-licenses [licenses]
  (let [localizations (->> (db/get-license-localizations)
                           (map #(update-in % [:langcode] keyword))
                           (group-by :licid))]
    (doall
     (for [lic licenses]
       (assoc lic :localizations
              (into {} (for [{:keys [langcode title textcontent]} (get localizations (:id lic))]
                         [langcode {:title title :textcontent textcontent}])))))))

(defn get-resource-licenses
  "Get resource licenses for given resource id"
  [id]
  (->> (db/get-resource-licenses {:id id})
       (format-licenses)
       (localize-licenses)))

;; NB! There are three different "license activity" concepts:
;; - start and end in resource_licenses table
;; - start and end in workflow_licenses table
;; - start and end in licenses table
;;
;; The last of these is only used in get-all-licenses which is only
;; used by /api/licenses. The resource and workflow activities are
;; used in actual application processing logic.

(defn get-all-licenses
  "Get all licenses.

   filters is a map of key-value pairs that must be present in the licenses"
  [filters]
  (let [filters (or filters {})]
    (->> (db/get-all-licenses)
         (map db/assoc-active)
         (filter #(db/contains-all-kv-pairs? % filters))
         (format-licenses)
         (localize-licenses))))

(defn get-active-licenses
  "Get license active now. Params map can contain:
    :wfid -- workflow to get workflow licenses for
    :items -- sequence of catalogue items to get resource licenses for"
  [now params]
  (->> (db/get-licenses params)
       (format-licenses)
       (localize-licenses)
       (filter (fn [license] (db/now-active? now (:start license) (:end license))))
       (distinct-by :id)))
