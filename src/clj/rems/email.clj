(ns rems.email
  (:require [clojure.string :as str]
            [postal.core :as postal]
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

(defn- form-link [app-id]
  (str context/*root-path* "/form/" app-id))

(defn- format-items [items]
  (str/join "," (map :title items)))

(defn confirm-application-creation [app-id items]
  (send-mail (get-user-mail)
             (text :t.email/application-sent-subject)
             (text-format :t.email/application-sent-msg
                          (get-username)
                          (format-items items)
                          (form-link app-id))))

(defn status-change-alert [recipient-attrs app-id items state]
  (send-mail (get-user-mail recipient-attrs)
             (text :t.email/status-changed-subject)
             (text-format :t.email/status-changed-msg
                          (get-username recipient-attrs)
                          app-id
                          (format-items items)
                          (localize-state state)
                          (form-link app-id))))

;; TODO: send message localized according to recipient's preferences, when those are stored
(defn- send-request [subject msg recipient-attrs applicant-name app-id items]
  (send-mail (get-user-mail recipient-attrs)
             (text subject)
             (text-format msg
                          (get-username recipient-attrs)
                          applicant-name
                          app-id
                          (format-items items)
                          (form-link app-id))))

(defn approval-request [recipient-attrs applicant-name app-id items]
  (send-request :t.email/approval-request-subject :t.email/approval-request-msg recipient-attrs applicant-name app-id items))

(defn review-request [recipient-attrs applicant-name app-id items]
  (send-request :t.email/review-request-subject :t.email/review-request-msg recipient-attrs applicant-name app-id items))

(defn action-not-needed [recipient-attrs applicant-name app-id]
  (send-mail (get-user-mail recipient-attrs)
             (text :t.email/action-not-needed-subject)
             (text-format :t.email/action-not-needed-msg
                          (get-username recipient-attrs)
                          applicant-name
                          app-id)))
