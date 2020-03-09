(ns rems.api.services.attachment
  (:require [rems.common.application-util :as application-util]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [ring.util.http-response :refer [ok content-type header]])
  (:import [java.io ByteArrayInputStream]))

(defn download [attachment]
  (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
      (header "Content-Disposition" (str "attachment;filename=" (pr-str (:attachment/filename attachment))))
      (content-type (:attachment/type attachment))))

(defn get-application-attachment [user-id attachment-id]
  (let [attachment (attachments/get-attachment attachment-id)]
    (when attachment
      ;; check that the user is allowed to read the application (may throw ForbiddenException)
      (applications/get-application user-id (:application/id attachment)))
    attachment))

(defn add-application-attachment [user-id application-id file]
  (let [application (applications/get-application user-id application-id)]
    (when-not (some #{:application.command/save-draft :application.command/remark}
                    (:application/permissions application))
      (throw-forbidden))
    (attachments/save-attachment! file user-id application-id)))
