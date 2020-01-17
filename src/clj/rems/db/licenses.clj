(ns rems.db.licenses
  "querying localized licenses"
  (:require [rems.common-util :refer [distinct-by]]
            [rems.db.core :as db]))

(defn- format-license [license]
  {:id (:id license)
   :licensetype (:type license)
   :organization (:organization license)
   :enabled (:enabled license)
   :archived (:archived license)})

(defn- format-licenses [licenses]
  (mapv format-license licenses))

(defn- get-license-localizations []
  (->> (db/get-license-localizations)
       (map #(update-in % [:langcode] keyword))
       (group-by :licid)))

(defn- localize-license [localizations license]
  (assoc license :localizations
         (into {} (for [{:keys [langcode title textcontent attachmentid]} (get localizations (:id license))]
                    [langcode {:title title
                               :textcontent textcontent
                               :attachment-id attachmentid}]))))

(defn- localize-licenses [licenses]
  (mapv (partial localize-license (get-license-localizations)) licenses))

(defn get-resource-licenses
  "Get resource licenses for given resource id"
  [id]
  (->> (db/get-resource-licenses {:id id})
       (format-licenses)
       (localize-licenses)))

(defn get-license
  "Get a single license by id"
  [id]
  (->> (db/get-license {:id id})
       (format-license)
       (localize-license (get-license-localizations))))

(defn get-all-licenses
  "Get all licenses.

   filters is a map of key-value pairs that must be present in the licenses"
  [filters]
  (->> (db/get-all-licenses)
       (db/apply-filters filters)
       (format-licenses)
       (localize-licenses)))

(defn get-licenses
  "Get licenses. Params map can contain:
     :wfid -- workflow to get workflow licenses for
     :items -- sequence of catalogue items to get resource licenses for"
  [params]
  (->> (db/get-licenses params)
       (format-licenses)
       (localize-licenses)
       (distinct-by :id)))
