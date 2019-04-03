(ns rems.db.attachments
  (:require [rems.api.applications-v2 :as applications-v2]
            [rems.application-util :refer [form-fields-editable?]]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db])
  (:import [java.io ByteArrayOutputStream FileInputStream File]))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} user-id application-id item-id]
  (let [application (applications-v2/api-get-application-v2 user-id application-id)
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

(defn remove-attachment!
  [user-id application-id item-id]
  (let [application (applications-v2/api-get-application-v2 user-id application-id)]
    (when-not (form-fields-editable? application)
      (throw-forbidden))
    (db/remove-attachment! {:application application-id
                            :form (get-in application [:application/form :form/id])
                            :item item-id})))
