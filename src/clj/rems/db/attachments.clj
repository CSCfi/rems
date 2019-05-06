(ns rems.db.attachments
  (:require [rems.application-util :refer [form-fields-editable?]]
            [rems.InvalidRequestException]
            #_[rems.db.applications :as applications]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db])
  (:import [java.io ByteArrayOutputStream FileInputStream File ByteArrayInputStream]
           rems.InvalidRequestException))

(defn- check-attachment-content-type
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
  (let [application ((resolve 'rems.db.applications/get-application) user-id application-id) ;; will throw if unauthorized?
        byte-array (with-open [input (FileInputStream. ^File tempfile)
                               buffer (ByteArrayOutputStream.)]
                     (clojure.java.io/copy input buffer)
                     (.toByteArray buffer))]
    (when-not (form-fields-editable? application)
      (throw-forbidden))
    (let [id (:id (db/save-attachment! {:application application-id
                                        :user user-id
                                        :filename filename
                                        :type content-type
                                        :data byte-array}))]
      {:id id
       :success true})))

(defn get-attachment [user-id application-id attachment-id]
  (let [application ((resolve 'rems.db.applications/get-application) user-id application-id)] ;; will throw if unauthorized?
    (when-let [attachment (db/get-attachment {:id attachment-id})]
      (assert (= (:appid attachment) application-id)
              {:got (:appid attachment) :wanted application-id})
      (check-attachment-content-type (:type attachment))
      {:data (-> (:data attachment)
                 (ByteArrayInputStream.))
       :content-type (:type attachment)})))

;; TODO no access control here, only in get-attachment
(defn get-attachments-for-application [application-id]
  (vec
   (for [{:keys [id filename type]} (db/get-attachments-for-application {:application-id application-id})]
     {:attachment/id id
      :attachment/filename filename
      :attachment/type type})))

(comment
  (get-attachments-for-application 1022))
