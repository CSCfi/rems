(ns rems.service.attachment
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [medley.core :refer [find-first]]
            [rems.application.commands :as commands]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.common.attachment-util :refer [getx-filename]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.util :refer [getx]]
            [ring.util.http-response :refer [ok content-type header]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.zip ZipOutputStream ZipEntry ZipException]))

(defn download [attachment]
  (let [filename (getx-filename attachment)]
    (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
        (header "Content-Disposition" (str "attachment;filename=" (pr-str filename)))
        (content-type (:attachment/type attachment)))))

(defn- find-application-attachment [user-id attachment]
  (when-some [application (->> attachment
                               :application/id
                               (applications/get-application-for-user user-id))]
    (->> application
         :application/attachments
         (find-first #(= (:attachment/id attachment)
                         (:attachment/id %))))))

(defn get-application-attachment
  "When user has sufficient view permissions, returns
   attachment with data with user permissions applied."
  [user-id attachment-id]
  (when-some [attachment (attachments/get-attachment attachment-id)]
    (cond
      (= user-id (:attachment/user attachment))
      attachment

      :else
      (if-some [att (find-application-attachment user-id attachment)]
        (merge att ; application attachment has user permissions applied
               (select-keys attachment [:attachment/data]))
        (throw-forbidden)))))

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
          (.putNextEntry zip (ZipEntry. (getx-filename metadata))) ; filename is potentially masked
          (.write zip (getx attachment :attachment/data))
          (.closeEntry zip)
          (catch ZipException e
            (log/warn "Ignoring attachment" (pr-str metadata) "when generating zip. Cause:" e)))))
    (-> (ok (ByteArrayInputStream. (.toByteArray out))) ; extra copy of the data here, could be more efficient
        (header "Content-Disposition" (str "attachment;filename=attachments-" (getx application :application/id) ".zip"))
        (content-type "application/zip"))))

