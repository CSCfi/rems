(ns rems.api.services.attachment
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [rems.application.commands :as commands]
            [rems.common.application-util :as application-util]
            [rems.auth.util :refer [throw-forbidden]]
            [rems.db.applications :as applications]
            [rems.db.attachments :as attachments]
            [rems.util :refer [getx]]
            [ring.util.http-response :refer [ok content-type header]])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [java.util.zip ZipOutputStream ZipEntry]))

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

(defn zip-attachments [application]
  (let [out (ByteArrayOutputStream.)]
    (with-open [zip (ZipOutputStream. out)]
      (doseq [metadata (getx application :application/attachments)]
        (let [id (getx metadata :attachment/id)
              attachment (attachments/get-attachment id)]
          ;; disambiguate filenames with id
          (.putNextEntry zip (ZipEntry. (add-postfix (getx attachment :attachment/filename) (str " (" id ")"))))
          (.write zip (getx attachment :attachment/data))
          (.closeEntry zip))))
    (-> (ok (ByteArrayInputStream. (.toByteArray out))) ;; extra copy of the data here, could be more efficient
        (header "Content-Disposition" (str "attachment;filename=attachments-" (getx application :application/id) ".zip"))
        (content-type "application/zip"))))
