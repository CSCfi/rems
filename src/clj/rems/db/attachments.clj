(ns rems.db.attachments
  (:require [rems.application-util :refer [form-fields-editable?]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db]
            [rems.util :refer [file-to-bytes]])
  (:import [rems InvalidRequestException]))

(defn check-attachment-content-type
  "Checks that content-type matches the allowed ones listed on the UI side:
   .pdf, .doc, .docx, .ppt, .pptx, .txt, image/*"
  [content-type]
  (when-not (or (#{"application/pdf"
                   "application/msword"
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                   "application/vnd.ms-powerpoint"
                   "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                   "text/plain"}
                 content-type)
                (.startsWith content-type "image/"))
    (throw (InvalidRequestException. (str "Unsupported content-type: " content-type)))))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} user-id application-id]
  (check-attachment-content-type content-type)
  (let [byte-array (file-to-bytes tempfile)
        id (:id (db/save-attachment! {:application application-id
                                      :user user-id
                                      :filename filename
                                      :type content-type
                                      :data byte-array}))]
    {:id id
     :success true}))

(defn get-attachment [attachment-id]
  (when-let [{:keys [type appid filename data]} (db/get-attachment {:id attachment-id})]
    (check-attachment-content-type type)
    {:application/id appid
     :attachment/filename filename
     :attachment/data data
     :attachment/type type}))

(defn get-attachments-for-application [application-id]
  (vec
   (for [{:keys [id filename type]} (db/get-attachments-for-application {:application-id application-id})]
     {:attachment/id id
      :attachment/filename filename
      :attachment/type type})))
