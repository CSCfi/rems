(ns rems.service.licenses
  "Serving licenses for API."
  (:require [clj-time.core :as time]
            [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [clojure.java.io])
  (:import [java.io FileInputStream ByteArrayOutputStream]))

(defn create-license! [{:keys [licensetype organization localizations]}]
  (util/check-allowed-organization! organization)
  (let [license (db/create-license! {:organization (:organization/id organization)
                                     :type licensetype})
        licid (:id license)]
    (doseq [[langcode localization] localizations]
      (db/create-license-localization! {:licid licid
                                        :langcode (name langcode)
                                        :title (:title localization)
                                        :textcontent (:textcontent localization)
                                        :attachmentId (:attachment-id localization)}))
    ;; reset-cache! not strictly necessary since licenses don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
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
     (db/create-license-attachment! {:user user-id
                                     :filename filename
                                     :type content-type
                                     :data byte-array
                                     :start (time/now)})
     [:id])))

(defn remove-license-attachment! [attachment-id]
  (db/remove-license-attachment! {:id attachment-id}))

(defn get-license-attachment [attachment-id]
  (when-let [attachment (db/get-license-attachment {:attachmentId attachment-id})]
    (attachments/check-allowed-attachment (:filename attachment))
    {:attachment/filename (:filename attachment)
     :attachment/data (:data attachment)
     :attachment/type (:type attachment)}))

(defn get-application-license-attachment [user-id application-id license-id language]
  (when-let [app (applications/get-application-for-user user-id application-id)]
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
  (db/set-license-enabled! {:id id :enabled enabled})
  {:success true})

(defn set-license-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (or (dependencies/change-archive-status-error archived  {:license/id id})
      (do
        (db/set-license-archived! {:id id
                                   :archived archived})
        {:success true})))

(defn get-all-licenses
  "Get all licenses.

   filters is a map of key-value pairs that must be present in the licenses"
  [filters]
  (->> (licenses/get-all-licenses filters)
       (mapv organizations/join-organization)))
