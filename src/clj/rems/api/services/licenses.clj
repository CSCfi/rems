(ns rems.api.services.licenses
  "Serving licenses for API."
  (:require [rems.common-util :refer [distinct-by]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.core :as db]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.workflow :as workflow])
  (:import [java.io FileInputStream ByteArrayOutputStream]))

(defn create-license! [{:keys [licensetype organization localizations]} user-id]
  (let [license (db/create-license! {:owneruserid user-id
                                     :modifieruserid user-id
                                     :organization (or organization "")
                                     :type licensetype})
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
  (attachments/check-attachment-content-type content-type)
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

(defn remove-license-attachment! [attachment-id]
  (db/remove-license-attachment! {:id attachment-id}))

(defn get-license-attachment [attachment-id]
  (when-let [attachment (db/get-license-attachment {:attachmentId attachment-id})]
    (attachments/check-attachment-content-type (:type attachment))
    {:attachment/filename (:filename attachment)
     :attachment/data (:data attachment)
     :attachment/type (:type attachment)}))

(defn get-application-license-attachment [user-id application-id license-id language]
  (when-let [app (applications/get-application user-id application-id)]
    (when-let [license (some #(when (= license-id (:license/id %)) %)
                             (:application/licenses app))]
      (when-let [attachment-id (get-in license [:license/attachment-id language])]
        (when-let [attachment (get-license-attachment attachment-id)]
          attachment)))))

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

(defn set-license-enabled! [command]
  (db/set-license-enabled! (select-keys command [:id :enabled]))
  {:success true})

(defn set-license-archived! [{:keys [id archived]}]
  (let [usage (get-license-usage id)]
    (if (and archived usage)
      {:success false
       :errors [(merge {:type :t.administration.errors/license-in-use}
                       usage)]}
      (do
        (db/set-license-archived! {:id id
                                   :archived archived})
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
