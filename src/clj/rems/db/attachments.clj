(ns rems.db.attachments
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.common.application-util :refer [form-fields-editable?]]
            [rems.common.attachment-types :as attachment-types]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.core :as db]
            [rems.util :refer [file-to-bytes]])
  (:import [rems InvalidRequestException]))

(defn check-allowed-attachment
  [filename]
  (when-not (attachment-types/allowed-extension? filename)
    (throw (InvalidRequestException. (str "Unsupported extension: " filename)))))

(defn get-attachment [attachment-id]
  (when-let [{:keys [modifieruserid type appid filename data]} (db/get-attachment {:id attachment-id})]
    (check-allowed-attachment filename)
    {:application/id appid
     :attachment/user modifieruserid
     :attachment/filename filename
     :attachment/data data
     :attachment/type type}))

(defn get-attachment-metadata [attachment-id]
  (when-let [{:keys [id modifieruserid type appid filename]} (db/get-attachment-metadata {:id attachment-id})]
    {:application/id appid
     :attachment/id id
     :attachment/user modifieruserid
     :attachment/filename filename
     :attachment/type type}))

(defn get-attachments-for-application [application-id]
  (vec
   (for [{:keys [id filename type]} (db/get-attachments-for-application {:application-id application-id})]
     {:attachment/id id
      :attachment/filename filename
      :attachment/type type})))

(defn- add-postfix [filename postfix]
  (if-let [i (str/last-index-of filename \.)]
    (str (subs filename 0 i) postfix (subs filename i))
    (str filename postfix)))

(deftest test-add-postfix
  (is (= "foo (1).txt"
         (add-postfix "foo.txt" " (1)")))
  (is (= "foo_bar_quux (1)"
         (add-postfix "foo_bar_quux" " (1)")))
  (is (= "foo.bar!.quux"
         (add-postfix "foo.bar.quux" "!")))
  (is (= "!"
         (add-postfix "" "!"))))

(defn- fix-filename [filename existing-filenames]
  (let [exists? (set existing-filenames)
        versions (cons filename
                       (map #(add-postfix filename (str " (" (inc %) ")"))
                            (range)))]
    (first (remove exists? versions))))

(deftest test-fix-filename
  (is (= "file.txt"
         (fix-filename "file.txt" ["file.pdf" "picture.gif"])))
  (is (= "file (1).txt"
         (fix-filename "file.txt" ["file.txt" "boing.txt"])))
  (is (= "file (2).txt"
         (fix-filename "file.txt" ["file.txt" "file (1).txt" "file (3).txt"]))))

(defn save-attachment!
  [{:keys [tempfile filename content-type]} user-id application-id]
  (check-allowed-attachment filename)
  (let [byte-array (file-to-bytes tempfile)
        filename (fix-filename filename (mapv :attachment/filename (get-attachments-for-application application-id)))
        id (:id (db/save-attachment! {:application application-id
                                      :user user-id
                                      :filename filename
                                      :type content-type
                                      :data byte-array}))]
    {:id id
     :success true}))

(defn copy-attachment! [new-application-id attachment-id]
  (let [attachment (db/get-attachment {:id attachment-id})]
    (:id (db/save-attachment! {:application new-application-id
                               :user (:modifieruserid attachment)
                               :filename (:filename attachment)
                               :type (:type attachment)
                               :data (:data attachment)}))))
