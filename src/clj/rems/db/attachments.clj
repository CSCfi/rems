(ns rems.db.attachments
  (:require [rems.application-util :refer [form-fields-editable?]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.core :as db])
  (:import [java.io ByteArrayOutputStream FileInputStream File]))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} user-id application-id item-id]
  (let [application (applications/get-dynamic-application-state-for-user user-id application-id)
        byte-array (with-open [input (FileInputStream. ^File tempfile)
                               buffer (ByteArrayOutputStream.)]
                     (clojure.java.io/copy input buffer)
                     (.toByteArray buffer))]
    (when-not (form-fields-editable? application)
      (throw-forbidden))
    (db/save-attachment! {:application application-id
                          :form (:form/id application)
                          :item item-id
                          :user user-id
                          :filename filename
                          :type content-type
                          :data byte-array})))

(defn remove-attachment!
  [user-id application-id item-id]
  (let [application (applications/get-dynamic-application-state-for-user user-id application-id)]
    (when-not (form-fields-editable? application)
      (throw-forbidden))
    (db/remove-attachment! {:application application-id
                            :form (:form/id application)
                            :item item-id})))
