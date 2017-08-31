(ns rems.email
  (:require [postal.core :as postal]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.text :refer :all]
            [rems.util :refer [get-user-mail
                               get-username]]))

(defn send-mail [to subject msg]
  (when-let [host (:smtp-host env)]
    (when-let [port (:smtp-port env)]
      (postal/send-message {:host host
                            :port port}
                           {:from (:mail-from env)
                            :to to
                            :subject subject
                            :body msg}))))

(defn- form-link [item-id app-id]
  (str context/*root-path* "/form/" item-id "/" app-id))

(defn confirm-application-creation [item-title item-id app-id]
  (send-mail (get-user-mail)
             (text :t.email/application-sent-subject)
             (text-format :t.email/application-sent-msg
                          (get-username)
                          item-title
                          (form-link item-id app-id))))

(defn status-change-alert [recipient-attrs app-id item-title state item-id]
  (send-mail (get-user-mail recipient-attrs)
             (text :t.email/status-changed-subject)
             (text-format :t.email/status-changed-msg
                          (get-username recipient-attrs)
                          app-id
                          item-title
                          (clojure.string/lower-case (localize-state state))
                          (form-link item-id app-id))))

(defn- send-request [subject msg recipient-attrs applicant-name app-id item-title item-id]
  (send-mail (get-user-mail recipient-attrs)
             (text subject)
             (text-format msg
                          (get-username recipient-attrs)
                          applicant-name
                          app-id
                          item-title
                          (form-link item-id app-id))))

(defn approval-request [recipient-attrs applicant-name app-id item-title item-id]
  (send-request :t.email/approval-request-subject :t.email/approval-request-msg recipient-attrs applicant-name app-id item-title item-id))

(defn review-request [recipient-attrs applicant-name app-id item-title item-id]
  (send-request :t.email/review-request-subject :t.email/review-request-msg recipient-attrs applicant-name app-id item-title item-id))
