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

(defn get-resource-licenses [id]
  (->> (db/get-resource-licenses {:id id})
       (format-licenses)
       (localize-licenses)))

(defn get-all-licenses [filters]
  (let [filters (or filters {})]
    (->> (db/get-all-licenses)
         (map db/assoc-active)
         (filter #(db/contains-all-kv-pairs? % filters))
         (format-licenses)
         (map #(dissoc % :start :end)) ;; HACK
         (localize-licenses))))

(defn get-licenses [params]
  (->> (db/get-licenses params)
       (format-licenses)
       (localize-licenses)))

(defn get-active-licenses [now params]
  (->> (get-licenses params)
       (filter (fn [license]
                 (let [start (:start license)
                       end (:end license)]
                   (and (or (nil? start) (time/before? start now))
                        (or (nil? end) (time/before? now end))))))
       (distinct-by :id)))
