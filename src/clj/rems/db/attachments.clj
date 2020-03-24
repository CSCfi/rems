(ns rems.db.attachments
  (:require [rems.common.application-util :refer [form-fields-editable?]]
            [rems.common.attachment-types :as attachment-types]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db]
            [rems.util :refer [file-to-bytes]])
  (:import [rems InvalidRequestException]))

(defn check-allowed-attachment
  [filename]
  (when-not (attachment-types/allowed-extension? filename)
    (throw (InvalidRequestException. (str "Unsupported extension: " filename)))))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} user-id application-id]
  (check-allowed-attachment filename)
  (let [byte-array (file-to-bytes tempfile)
        id (:id (db/save-attachment! {:application application-id
                                      :user user-id
                                      :filename filename
                                      :type content-type
                                      :data byte-array}))]
    {:id id
     :success true}))

(defn get-attachment [attachment-id]
  (when-let [{:keys [modifieruserid type appid filename data]} (db/get-attachment {:id attachment-id})]
    (check-allowed-attachment filename)
    {:application/id appid
     :attachment/user modifieruserid
     :attachment/filename filename
     :attachment/data data
     :attachment/type type}))

(defn get-attachment-metadata [attachment-id]
  (when-let [{:keys [id modifieruserid type appid filename]} (db/get-attachment-metadata {:id attachment-id})]
    {:application/id appid
     :attachment/id id
     :attachment/user modifieruserid
     :attachment/filename filename
     :attachment/type type}))

(defn get-attachments-for-application [application-id]
  (vec
   (for [{:keys [id filename type]} (db/get-attachments-for-application {:application-id application-id})]
     {:attachment/id id
      :attachment/filename filename
      :attachment/type type})))
