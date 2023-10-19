(ns rems.db.attachments
  (:require [clojure.java.shell :refer [sh]]
            [clojure.test :refer [deftest is]]
            [clojure.tools.logging :as log]
            [rems.common.attachment-util :as attachment-util]
            [rems.common.util :refer [fix-filename]]
            [rems.config :refer [env]]
            [rems.db.core :as db]
            [rems.util :refer [file-to-bytes]])
  (:import [rems PayloadTooLargeException UnsupportedMediaTypeException InvalidRequestException]))

(defn check-size
  [file]
  (when (= :too-large (:error file)) ; set already by the multipart upload wrapper
    (throw (PayloadTooLargeException. (str "File is too large")))))

(defn check-allowed-attachment
  [filename]
  (when-not (attachment-util/allowed-extension? filename)
    (throw (UnsupportedMediaTypeException. (str "Unsupported extension: " filename)))))

(defn get-attachment [attachment-id]
  (when-let [{:keys [id userid type appid filename data]} (db/get-attachment {:id attachment-id})]
    (check-allowed-attachment filename)
    {:application/id appid
     :attachment/id id
     :attachment/user userid
     :attachment/filename filename
     :attachment/data data
     :attachment/type type}))

(defn scan-for-malware
  "Feeds byte-array to STDIN and runs executable at malware-scanner-path returns true if malware executable returns a non-zero status-code, false otherwise, logs STERR of executable"
  [malware-scanner-path byte-array]
  (let [scan-output (sh "sh" "-c" malware-scanner-path :in byte-array)]
    {:detected (not= (:exit scan-output) 0)
     :log (:err scan-output)}))

(deftest test-passing-scan-without-output
  (let [scan (scan-for-malware "test-data/malware-scanner-executables/pass-without-output.sh" "")]
    (is (and (not (:detected scan)) (nil? (:log scan))))))

(deftest test-failing-scan-without-output
  (let [scan (scan-for-malware "test-data/malware-scanner-executables/fail-without-output.sh" "")]
    (is (and (:detected scan) (nil? (:log scan))))))

(deftest test-passing-scan-with-output
  (let [scan (scan-for-malware "test-data/malware-scanner-executables/pass-with-output.sh" "")]
    (is (and (not (:detected scan)) (= (:log scan) "passed")))))

(deftest test-failing-scan-with-output
  (let [scan (scan-for-malware "test-data/malware-scanner-executables/fail-with-output.sh" "")]
    (is (and (:detected scan) (= (:log scan) "failed")))))

(deftest test-scanner-receives-input
  (let [input "miauw"
        scan (scan-for-malware "test-data/malware-scanner-executables/cat-to-stderr.sh" input)]
    (is (and (not (:detected scan)) (= (:log scan) input)))))

(defn check-for-malware-if-enabled [byte-array]
  (when-let [malware-scanner-path (str (:malware-scanner-path env))]
    (let [scan (scan-for-malware malware-scanner-path byte-array)]
      (when (and (:enable-malware-scanner-logging env) (seq (:log scan)))
        (log/info (:log scan)))
      (when (:detected scan)
        (throw (InvalidRequestException. (str "Malware detected")))))))

(defn get-attachments
  "Gets attachments without the data."
  []
  (for [{:keys [id userid type appid filename]} (db/get-attachments)]
    (do
      (check-allowed-attachment filename)
      {:application/id appid
       :attachment/id id
       :attachment/user userid
       :attachment/filename filename
       :attachment/type type})))

(defn get-attachment-metadata [attachment-id]
  (when-let [{:keys [id userid type appid filename]} (db/get-attachment-metadata {:id attachment-id})]
    {:application/id appid
     :attachment/id id
     :attachment/user userid
     :attachment/filename filename
     :attachment/type type}))

(defn get-attachments-for-application [application-id]
  (vec
   (for [{:keys [id filename type userid]} (db/get-attachments-for-application {:application-id application-id})]
     {:attachment/id id
      :attachment/user userid
      :attachment/filename filename
      :attachment/type type})))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} user-id application-id]
  (check-allowed-attachment filename)
  (let [byte-array (file-to-bytes tempfile)]
    (check-for-malware-if-enabled byte-array)
    (let [filename (fix-filename filename (mapv :attachment/filename (get-attachments-for-application application-id)))
          id (:id (db/save-attachment! {:application application-id
                                        :user user-id
                                        :filename filename
                                        :type content-type
                                        :data byte-array}))]
      {:id id
       :success true})))

(defn update-attachment!
  "Updates the attachment, but does not modify the file data! Also does not \"fix the filename\"."
  [attachment]
  (check-allowed-attachment (:attachment/filename attachment))
  (db/update-attachment! {:id (:attachment/id attachment)
                          :application (:application/id attachment)
                          :user (:attachment/user attachment)
                          :filename (:attachment/filename attachment)
                          :type (:attachment/type attachment)})
  {:id (:attachment/id attachment)
   :success true})

(defn redact-attachment!
  "Updates the attachment by zeroing the file data."
  [attachment-id]
  (db/redact-attachment! {:id attachment-id})
  {:id attachment-id
   :success true})

(defn copy-attachment! [new-application-id attachment-id]
  (let [attachment (db/get-attachment {:id attachment-id})]
    (:id (db/save-attachment! {:application new-application-id
                               :user (:userid attachment)
                               :filename (:filename attachment)
                               :type (:type attachment)
                               :data (:data attachment)}))))

(defn delete-attachment! [attachment-id]
  (db/delete-attachment! {:id attachment-id}))

