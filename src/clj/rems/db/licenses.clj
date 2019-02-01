(ns rems.db.licenses
  "querying localized licenses"
  (:require [clj-time.core :as time]
            [rems.db.core :as db]
            [rems.common-util :refer [distinct-by]]
            [rems.util :refer [getx-user-id]]
            [clojure.tools.logging :as log])
  (:import (java.io FileInputStream ByteArrayOutputStream)))

(defn- format-license [license]
  {:id (:id license)
   :licensetype (:type license)
   :start (:start license)
   :end (:endt license)
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
       (localize-licenses)))

(defn get-license
  "Get a single license by id"
  [id]
  (->> (db/get-license {:id id})
       (format-license)
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
       (map db/assoc-active)
       (db/apply-filters filters)
       (format-licenses)
       (localize-licenses)))

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

(defn create-license! [{:keys [title licensetype textcontent localizations attachment-id]}]
  (let [license (db/create-license! {:owneruserid (getx-user-id)
                                     :modifieruserid (getx-user-id)
                                     :type licensetype
                                     :title title
                                     :textcontent textcontent
                                     :attachmentId attachment-id})
        licid (:id license)]
    (doseq [[langcode localization] localizations]
      (db/create-license-localization! {:licid licid
                                        :langcode (name langcode)
                                        :title (:title localization)
                                        :textcontent (:textcontent localization)
                                        :attachmentId (:attachment-id localization)}))
    {:id licid}))

(defn create-license-attachment! [{:keys [tempfile filename content-type]} user-id]
  (let [byte-array (with-open [input (FileInputStream. tempfile)
                               buffer (ByteArrayOutputStream.)]
                     (clojure.java.io/copy input buffer)
                     (.toByteArray buffer))]
    (select-keys
     (db/create-license-attachment! {:user user-id
                                     :filename filename
                                     :type content-type
                                     :data byte-array})
     [:id])))

(defn remove-license-attachment!
  [attachment-id]
  (db/remove-license-attachment! {:id attachment-id}))
