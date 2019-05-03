(ns rems.db.attachments
  (:require [rems.application-util :refer [form-fields-editable?]]
            [rems.db.applications :as applications]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db])
  (:import [java.io ByteArrayOutputStream FileInputStream File ByteArrayInputStream]
           [rems InvalidRequestException]))

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
  [{:keys [tempfile filename content-type]} user-id application-id item-id]
  (check-attachment-content-type content-type)
  (let [application (applications/get-application user-id application-id)
        byte-array (with-open [input (FileInputStream. ^File tempfile)
                               buffer (ByteArrayOutputStream.)]
                     (clojure.java.io/copy input buffer)
                     (.toByteArray buffer))]
    (when-not (form-fields-editable? application)
      (throw-forbidden))
    (db/save-attachment! {:application application-id
                          :form (get-in application [:application/form :form/id])
                          :item item-id
                          :user user-id
                          :filename filename
                          :type content-type
                          :data byte-array})))

(defn remove-attachment! [user-id application-id item-id]
  (let [application (applications/get-application user-id application-id)]
    (when-not (form-fields-editable? application)
      (throw-forbidden))
    (db/remove-attachment! {:application application-id
                            :form (get-in application [:application/form :form/id])
                            :item item-id})))

(defn get-attachment [user-id application-id field-id]
  (let [application (applications/get-application user-id application-id)
        form-id (get-in application [:application/form :form/id])]
    (when-let [attachment (db/get-attachment {:item field-id
                                              :form form-id
                                              :application application-id})]
      (check-attachment-content-type (:type attachment))
      {:data (-> (:data attachment)
                 (ByteArrayInputStream.))
       :content-type (:type attachment)})))
