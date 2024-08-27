(ns rems.service.licenses
  "Serving licenses for API."
  (:require [rems.service.dependencies :as dependencies]
            [rems.service.util :as util]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.db.licenses :as licenses]
            [rems.db.organizations :as organizations]
            [rems.util :refer [file-to-bytes]]))

(defn create-license! [{:keys [licensetype organization localizations]}]
  (util/check-allowed-organization! organization)
  (let [id (rems.db.licenses/create-license! {:license-type licensetype
                                              :organization-id (:organization/id organization)
                                              :localizations localizations})]
    ;; reset-cache! not strictly necessary since licenses don't depend on anything, but here for consistency
    (dependencies/reset-cache!)
    {:success (some? id)
     :id id}))

(defn create-license-attachment! [{:keys [tempfile filename content-type] :as file} userid]
  (attachments/check-size file)
  (attachments/check-allowed-attachment filename)
  (let [byte-array (file-to-bytes tempfile)]
    (attachments/check-for-malware-if-enabled byte-array)
    {:id (attachments/create-license-attachment! {:userid userid
                                                  :filename filename
                                                  :content-type content-type
                                                  :data byte-array})}))

(defn remove-license-attachment! [attachment-id]
  (attachments/remove-license-attachment! attachment-id))

(defn get-license-attachment [attachment-id]
  (when-let [attachment (attachments/get-license-attachment attachment-id)]
    (attachments/check-allowed-attachment attachment)
    attachment))

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
    (-> license
        organizations/join-organization)))

(defn set-license-enabled! [{:keys [id enabled]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (rems.db.licenses/set-enabled! id enabled)
  {:success true})

(defn set-license-archived! [{:keys [id archived]}]
  (util/check-allowed-organization! (:organization (get-license id)))
  (or (dependencies/change-archive-status-error archived  {:license/id id})
      (do
        (rems.db.licenses/set-archived! id archived)
        {:success true})))

(defn get-all-licenses
  [filters]
  (->> (licenses/get-licenses filters)
       (mapv organizations/join-organization)))
