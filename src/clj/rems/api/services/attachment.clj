(ns rems.api.services.attachment
  (:require [clojure.set :as set]
            [rems.common.application-util :as application-util]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [ring.util.http-response :refer [ok content-type header]])
  (:import [java.io ByteArrayInputStream]))

(defn download [attachment]
  (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
      (header "Content-Disposition" (str "attachment;filename=" (pr-str (:attachment/filename attachment))))
      (content-type (:attachment/type attachment))))


(defn- attachment-visible? [application attachment-id]
  (let [from-events (mapcat :event/attachments (:application/events application))
        from-fields (for [field (get-in application [:application/form :form/fields])
                          :when (= :attachment (:field/type field))
                          value [(:field/value field) (:field/previous-value field)]
                          :when value]
                      (Integer/parseInt value))]
    (contains? (set (concat from-events from-fields))
               attachment-id)))

;; TODO refactor
(defn get-application-attachment [user-id attachment-id]
  (let [attachment (attachments/get-attachment attachment-id)]
    (when attachment
      ;; check that the user is allowed to read the application (may throw ForbiddenException)
      (let [app (applications/get-application user-id (:application/id attachment))]
        ;; and further check that the attachment is visible
        (when-not (or (= user-id (:attachment/user attachment))
                      (attachment-visible? app attachment-id))
          (throw-forbidden))))
    attachment))

(defn add-application-attachment [user-id application-id file]
  (let [application (applications/get-application user-id application-id)]
    (when-not (some #{:application.command/save-draft :application.command/remark}
                    (:application/permissions application))
      (throw-forbidden))
    (attachments/save-attachment! file user-id application-id)))
