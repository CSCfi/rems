(ns rems.service.attachment
  (:require [better-cond.core :as b]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [medley.core :refer [find-first]]
            [rems.application.commands :as commands]
            [rems.application.model :as model]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.common.attachment-util :refer [allowed-extension? getx-filename]]
            [rems.common.util :refer [fix-filename getx]]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.multipart :refer [scan-for-malware]]
            [rems.util :refer [file-to-bytes]]
            [ring.util.http-response :refer :all])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.zip ZipOutputStream ZipEntry ZipException]
           [rems InvalidRequestException PayloadTooLargeException UnsupportedMediaTypeException]))

(defn check-size [x]
  (when (= :too-large (:error x)) ; set already by the multipart upload wrapper
    (throw (PayloadTooLargeException. (str "File is too large")))))

(defn check-allowed-attachment [x]
  (let [filename (or (:attachment/filename x)
                     (:filename x)
                     x)]
    (when-not (allowed-extension? filename)
      (throw (UnsupportedMediaTypeException. (str "Unsupported extension: " filename))))))

(defn check-for-malware-if-enabled [data]
  (when-let [malware-scanner-path (:scanner-path (:malware-scanning env))]
    (let [scan (scan-for-malware malware-scanner-path data)]
      (when (:logging (:malware-scanning env))
        (when (seq (:out scan)) (log/info (:out scan)))
        (when (seq (:err scan)) (log/error (:err scan))))
      (when (:detected scan)
        (throw (InvalidRequestException. "Malware detected"))))))

(defn download [attachment]
  (let [filename (getx-filename attachment)]
    (-> (ok (ByteArrayInputStream. (:attachment/data attachment)))
        (header "Content-Disposition" (str "attachment;filename=" (pr-str filename)))
        (content-type (:attachment/type attachment)))))

(defn get-application-attachment
  "When user has sufficient view permissions, returns
   attachment with data with user permissions applied."
  [user-id attachment-id]
  (b/cond
    :when-some [att (attachments/get-attachment attachment-id)]

    ;; user owns this attachment
    (= user-id (:attachment/user att))
    (assoc att :attachment/data (attachments/get-attachment-data attachment-id))

    ;; user has permission (in application) to see this attachment
    ;; returned attachment has any possible restrictions applied to
    :let [att (->> (applications/get-application-for-user user-id (:application/id att))
                   :application/attachments
                   (find-first (comp #{attachment-id} :attachment/id)))]
    (some? att)
    (assoc att :attachment/data (attachments/get-attachment-data attachment-id))

    :else
    (throw-forbidden)))

(defn save-attachment! [{:keys [file user-id application-id]}]
  (check-size file)
  (check-allowed-attachment file)
  (let [data (file-to-bytes (:tempfile file))]
    (check-for-malware-if-enabled data)
    {:id (rems.db.attachments/save-attachment! {:data data
                                                :filename (:filename file)
                                                :content-type (:content-type file)
                                                :user-id user-id
                                                :application-id application-id})
     :success true}))

(defn create-license-attachment! [{:keys [file user-id]}]
  (check-size file)
  (check-allowed-attachment file)
  (let [data (file-to-bytes (:tempfile file))]
    (check-for-malware-if-enabled data)
    {:id (rems.db.attachments/create-license-attachment! {:data data
                                                          :filename (:filename file)
                                                          :content-type (:content-type file)
                                                          :user-id user-id})
     :success true}))

(defn add-application-attachment [user-id application-id file]
  (b/cond
    :let [application (rems.db.applications/get-application-for-user user-id application-id)
          can-save-attachment (some (set/union commands/commands-with-comments
                                               #{:application.command/save-draft})
                                    (:application/permissions application))]
    (not can-save-attachment)
    (throw-forbidden)

    ;; else
    :let [all-application-attachments (->> (rems.db.attachments/get-attachments-for-application application-id)
                                           (mapv :attachment/filename))]
    (save-attachment! {:file (update file :filename fix-filename all-application-attachments)
                       :user-id user-id
                       :application-id application-id})))

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
                    data (attachments/get-attachment-data id)]
              :when (or all? (contains? (get classes id) :field/value))]
        ;; we deduplicate filenames when uploading, but here's a
        ;; failsafe in case we have duplicate filenames in old
        ;; applications
        (try
          (assert data)
          (doto zip
            (.putNextEntry (ZipEntry. (getx-filename metadata))) ; filename is potentially masked
            (.write data)
            (.closeEntry))
          (catch ZipException e
            (log/warn "Ignoring attachment" (pr-str metadata) "when generating zip. Cause:" e)))))
    (-> (ok (ByteArrayInputStream. (.toByteArray out))) ; extra copy of the data here, could be more efficient
        (header "Content-Disposition" (str "attachment;filename=attachments-" (getx application :application/id) ".zip"))
        (content-type "application/zip"))))

(defn remove-license-attachment! [attachment-id]
  (rems.db.attachments/remove-license-attachment! attachment-id)
  {:success true})

(defn get-license-attachment [attachment-id]
  (when-let [attachment (rems.db.attachments/get-license-attachment-metadata attachment-id)]
    (-> attachment
        rems.db.attachments/join-license-attachment-data)))

(defn get-application-license-attachment [user-id application-id license-id language]
  (b/when-let [app (rems.db.applications/get-application-for-user user-id application-id)
               license (->> (:application/licenses app)
                            (find-first (comp #{license-id} :license/id)))
               attachment-id (get-in license [:license/attachment-id language])]
    (get-license-attachment attachment-id)))

