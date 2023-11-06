(ns rems.service.licenses
  "Serving licenses for API."
  (:require [clj-time.core :as time]
            [clojure.java.io]
            [rems.db.attachments :as attachments]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.service.cache :as cache]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util])
  (:import [java.io FileInputStream ByteArrayOutputStream]))

(defn create-license! [{:keys [licensetype organization localizations]}]
  (util/check-allowed-organization! organization)
  (let [license (licenses/create-license! {:organization (:organization/id organization)
                                           :type licensetype})
        licid (:id license)]
    (doseq [[langcode localization] localizations]
      (licenses/create-license-localization! {:licid licid
                                              :langcode (name langcode)
                                              :title (:title localization)
                                              :textcontent (:textcontent localization)
                                              :attachmentId (:attachment-id localization)}))
    {:success (not (nil? licid))
     :id licid}))

(defn create-license-attachment! [{:keys [tempfile filename content-type] :as file} user-id]
  (attachments/check-size file)
  (attachments/check-allowed-attachment filename)
  (let [byte-array (with-open [input (FileInputStream. tempfile)
                               buffer (ByteArrayOutputStream.)]
                     (clojure.java.io/copy input buffer)
                     (.toByteArray buffer))]
    (select-keys
     (licenses/create-license-attachment! {:user user-id
                                           :filename filename
                                           :type content-type
                                           :data byte-array
                                           :start (time/now)})
     [:id])))

(defn remove-license-attachment! [attachment-id]
  (licenses/remove-license-attachment! {:id attachment-id}))

(defn get-license-attachment [attachment-id]
  (when-let [attachment (licenses/get-license-attachment {:attachmentId attachment-id})]
    (attachments/check-allowed-attachment (:filename attachment))
    {:attachment/filename (:filename attachment)
     :attachment/data (:data attachment)
     :attachment/type (:type attachment)}))

(defn get-application-license-attachment [user-id application-id license-id language]
  (when-let [app (cache/get-full-personalized-application-for-user user-id application-id)]
    (when-let [license (some #(when (= license-id (:license/id %)) %)
                             (:application/licenses app))]
      (when-let [attachment-id (get-in license [:license/attachment-id language])]
        (when-let [attachment (get-license-attachment attachment-id)]
          attachment)))))

(defn get-license
  "Get a single license by id"
  [id]
  (when-let [license (licenses/get-license id)]
    (organizations/join-organization license)))

(defn set-license-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (licenses/set-license-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-license-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (or (dependencies/change-archive-status-error archived  {:license/id id})
      (do
        (licenses/set-license-archived! {:id id
                                         :archived archived})
        {:success true})))

(defn get-all-licenses
  "Get all licenses.

   filters is a map of key-value pairs that must be present in the licenses"
  [filters]
  (->> (licenses/get-all-licenses filters)
       (mapv organizations/join-organization)))
