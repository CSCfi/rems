(ns rems.api.services.attachment
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [rems.application.commands :as commands]
            [rems.common.application-util :as application-util]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.util :refer [getx]]
            [ring.util.http-response :refer [ok content-type header]]
            [ring.util.io :as ring-io])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.zip ZipOutputStream ZipEntry ZipException]))

(defn download [attachment]
  (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
      (header "Content-Disposition" (str "attachment;filename=" (pr-str (:attachment/filename attachment))))
      (content-type (:attachment/type attachment))))

(defn- contains-attachment? [application attachment-id]
  (some #(= attachment-id (:attachment/id %))
        (:application/attachments application)))

(defn get-application-attachment [user-id attachment-id]
  (let [attachment (attachments/get-attachment attachment-id)]
    (cond
      (nil? attachment)
      nil

      (= user-id (:attachment/user attachment))
      attachment

      (contains-attachment? (applications/get-application user-id (:application/id attachment))
                            attachment-id)
      attachment

      :else
      (throw-forbidden))))

(defn add-application-attachment [user-id application-id file]
  (let [application (applications/get-application user-id application-id)]
    (when-not (some (set/union commands/commands-with-comments
                               #{:application.command/save-draft})
                    (:application/permissions application))
      (throw-forbidden))
    (attachments/save-attachment! file user-id application-id)))

(defn zip-attachments [application]
  (let [zip-input (ring-io/piped-input-stream
                   (fn [out]
                     (with-open [zip (ZipOutputStream. out)]
                       (doseq [metadata (getx application :application/attachments)]
                         (let [id (getx metadata :attachment/id)
                               attachment (attachments/get-attachment id)]
                           ;; we deduplicate filenames when uploading, but here's a
                           ;; failsafe in case we have duplicate filenames in old
                           ;; applications
                           (try
                             (.putNextEntry zip (ZipEntry. (getx attachment :attachment/filename)))
                             (.write zip (getx attachment :attachment/data))
                             (.closeEntry zip)
                             (catch ZipException e
                               (log/warn "Ignoring attachment" (pr-str metadata) "when generating zip. Cause:" e))))))))]
    (-> (ok zip-input)
        (header "Content-Disposition" (str "attachment;filename=attachments-" (getx application :application/id) ".zip"))
        (content-type "application/zip"))))
