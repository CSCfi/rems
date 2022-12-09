(ns rems.service.attachment
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [medley.core :refer [assoc-some find-first]]
            [rems.application.commands :as commands]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.util :refer [getx]]
            [ring.util.http-response :refer [ok content-type header]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.zip ZipOutputStream ZipEntry ZipException]))

(defn download [attachment]
  (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
      (header "Content-Disposition" (str "attachment;filename=" (pr-str (:attachment/filename attachment))))
      (content-type (:attachment/type attachment))))

(defn get-application-attachment [user-id attachment-id]
  (let [attachment (attachments/get-attachment attachment-id)]
    (cond
      (nil? attachment)
      nil

      (= user-id (:attachment/user attachment))
      attachment

      :else
      (let [application (applications/get-application-for-user user-id (:application/id attachment))
            application-attachment (->> (:application/attachments application)
                                        (find-first #(= attachment-id (:attachment/id %))))
            redacted? (= :filename/redacted (:attachment/filename application-attachment))]
        (if (some? application-attachment) ; user can see the attachment
          (assoc-some attachment :attachment/filename (when redacted?
                                                        "redacted"))
          (throw-forbidden))))))

(defn add-application-attachment [user-id application-id file]
  (attachments/check-size file)
  (attachments/check-allowed-attachment (:filename file))
  (let [application (applications/get-application-for-user user-id application-id)]
    (when-not (some (set/union commands/commands-with-comments
                               #{:application.command/save-draft})
                    (:application/permissions application))
      (throw-forbidden))
    (attachments/save-attachment! file user-id application-id)))

(defn get-attachments-in-use
  "Returns the attachment ids actually in use (field answer or event)."
  [application]
  (keys (model/classify-attachments application)))

(defn zip-attachments [application all?]
  (let [classes (model/classify-attachments application)
        out (ByteArrayOutputStream.)]
    (with-open [zip (ZipOutputStream. out)]
      (doseq [metadata (getx application :application/attachments)
              :let [id (getx metadata :attachment/id)
                    attachment (attachments/get-attachment id)]
              :when (or all? (contains? (get classes id) :field/value))]
        ;; we deduplicate filenames when uploading, but here's a
        ;; failsafe in case we have duplicate filenames in old
        ;; applications
        (try
          (.putNextEntry zip (ZipEntry. (let [filename (getx metadata :attachment/filename)]
                                          (if (= :filename/redacted filename)
                                            "redacted"
                                            filename))))
          (.write zip (getx attachment :attachment/data))
          (.closeEntry zip)
          (catch ZipException e
            (log/warn "Ignoring attachment" (pr-str metadata) "when generating zip. Cause:" e)))))
    (-> (ok (ByteArrayInputStream. (.toByteArray out))) ;; extra copy of the data here, could be more efficient
        (header "Content-Disposition" (str "attachment;filename=attachments-" (getx application :application/id) ".zip"))
        (content-type "application/zip"))))
