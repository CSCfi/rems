(ns rems.api.services.licenses
  "Serving licenses for API."
  (:require [rems.common-util :refer [distinct-by]]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.workflow :as workflow])
  (:import (java.io FileInputStream ByteArrayOutputStream)))

(defn create-license! [{:keys [title licensetype textcontent localizations attachment-id]} user-id]
  (let [license (db/create-license! {:owneruserid user-id
                                     :modifieruserid user-id
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
    {:success (not (nil? licid))
     :id licid}))

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

(defn- get-license-usage [id]
  ;; these could be db joins
  (let [resources (->> (db/get-resources-for-license {:id id})
                       (map :resid)
                       (map resource/get-resource)
                       (remove :archived)
                       (map #(select-keys % [:id :resid])))
        workflows (->> (db/get-workflows-for-license {:id id})
                       (map :wfid)
                       (map workflow/get-workflow)
                       (remove :archived)
                       (map #(select-keys % [:id :title])))]
    (when (or (seq resources) (seq workflows))
      {:resources resources
       :workflows workflows})))

(defn update-license! [command]
  (let [usage (get-license-usage (:id command))]
    (if (and (:archived command) usage)
      {:success false
       :errors [(merge {:type :t.administration.errors/license-in-use}
                       usage)]}
      (do
        (db/set-license-state! command)
        {:success true}))))

(defn get-license
  "Get a single license by id"
  [id]
  (licenses/get-license id))

(defn get-all-licenses
  "Get all licenses.

   filters is a map of key-value pairs that must be present in the licenses"
  [filters]
  (licenses/get-all-licenses filters))
