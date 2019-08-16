(ns rems.db.licenses
  "querying localized licenses"
  (:require [rems.common-util :refer [distinct-by]]
            [rems.db.core :as db]))

(defn- format-license [license]
  {:id (:id license)
   :licensetype (:type license)
   :start (:start license)
   :end (:end license)
   :enabled (:enabled license)
   :archived (:archived license)
   :expired (:expired license)
   ;; TODO why do licenses have a non-localized title & content while items don't?
   :title (:title license)
   :textcontent (:textcontent license)
   :attachment-id (:attachmentid license)})

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
       (mapv db/assoc-expired)
       (localize-licenses)))

(defn get-license
  "Get a single license by id"
  [id]
  (->> (db/get-license {:id id})
       (format-license)
       (db/assoc-expired)
       (localize-license (get-license-localizations))))

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
  (->> (db/get-all-licenses)
       (map db/assoc-expired)
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

(defn get-active-licenses
  "Get licenses active now. Params map similar to get-licenses."
  [now params]
  (->> (get-licenses params)
       (map (partial db/assoc-expired now))
       (remove :expired)))
