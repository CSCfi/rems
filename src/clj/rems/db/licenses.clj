(ns rems.db.licenses
  "querying localized licenses"
  (:require [clj-time.core :as time]
            [rems.db.core :as db]))

(defn- get-licenses-raw [params]
  (doall
   (for [license (db/get-licenses params)]
     {:id (:id license)
      :licensetype (:type license)
      :start (:start license)
      :end (:endt license)
      ;; TODO why do licenses have a non-localized title & content while items don't?
      :title (:title license)
      :textcontent (:textcontent license)})))

(defn get-licenses [params]
  (let [localizations (->> (db/get-license-localizations)
                           (map #(update-in % [:langcode] keyword))
                           (group-by :licid))]
    (doall
     (for [lic (get-licenses-raw params)]
       (assoc lic :localizations
              (into {} (for [{:keys [langcode title textcontent]} (get localizations (:id lic))]
                         [langcode {:title title :textcontent textcontent}])))))))


(defn get-active-licenses [now params]
  (->> (get-licenses params)
       (filter (fn [license]
                 (let [start (:start license)
                       end (:end license)]
                   (and (or (nil? start) (time/before? start now))
                        (or (nil? end) (time/before? now end))))))))
