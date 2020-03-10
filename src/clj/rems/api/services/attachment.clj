(ns rems.api.services.attachment
  (:require [clojure.set :as set]
            [clojure.test :refer :all]
            [rems.application.commands :as commands]
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
