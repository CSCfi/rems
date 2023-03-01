(ns rems.email.template
  (:require [clojure.string :as str]
            [rems.common.application-util :as application-util]
            [rems.application.model]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.user-settings :as user-settings]
            [rems.text :refer [localize-utc-date text text-no-fallback text-format with-language]]
            [rems.util :as util]))

;;; Mapping events to emails

;; TODO list of resources?
;; TODO use real name when addressing user?

;; move this to a util namespace if its needed somewhere else
(defn- link-to-application [application-id]
  (str (:public-url env) "application/" application-id))

(defn- invitation-link [token]
  (str (:public-url env) "accept-invitation?token=" token))

(defn- format-application-for-email [application]
  (str
   (case (util/getx env :application-id-column)
     :generated-and-assigned-external-id (:application/external-id application)
     :external-id (:application/external-id application)
     :id (:application/id application))
   (when-not (empty? (:application/description application))
     (str ", \"" (:application/description application) "\""))))

(defn- resources-for-email [application]
  (->> (:application/resources application)
       (map #(get-in % [:catalogue-item/title context/*lang*]))
       (str/join ", ")))

(defn- handlers [application]
  (when (:enable-handler-emails env)
    (get-in application [:application/workflow :workflow.dynamic/handlers])))

(defn- other-handlers [event application]
  (filter #(not= (:userid %) (:event/actor event)) (handlers application)))

(defmulti event-to-emails
  (fn [event _application] (:event/type event)))

(defmethod event-to-emails :default [_event _application]
  [])

(defn- emails-to-recipients [recipients event application subject-text body-text]
  (vec
   (for [recipient recipients
         :let [email (with-language (:language (user-settings/get-user-settings (:userid recipient)))
                       (fn []
                         (when (and body-text (not (str/blank? (text-no-fallback body-text))))
                           {:to-user (:userid recipient)
                            :subject (text-format subject-text
                                                  (application-util/get-member-name recipient)
                                                  (application-util/get-member-name (:event/actor-attributes event))
                                                  (format-application-for-email application)
                                                  (application-util/get-applicant-name application)
                                                  (resources-for-email application)
                                                  (link-to-application (:application/id event)))
                            :body (str
                                   (text-format body-text
                                                (application-util/get-member-name recipient)
                                                (application-util/get-member-name (:event/actor-attributes event))
                                                (format-application-for-email application)
                                                (application-util/get-applicant-name application)
                                                (resources-for-email application)
                                                (link-to-application (:application/id event)))
                                   (text :t.email/regards)
                                   (text :t.email/footer))})))]
         :when email]
     email)))

(defmethod event-to-emails :application.event/approved [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-approved/subject-to-applicant
                                :t.email.application-approved/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-approved/subject-to-handler
                                :t.email.application-approved/message-to-handler)))

(defmethod event-to-emails :application.event/rejected [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-rejected/subject-to-applicant
                                :t.email.application-rejected/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-rejected/subject-to-handler
                                :t.email.application-rejected/message-to-handler)))

(defmethod event-to-emails :application.event/revoked [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-revoked/subject-to-applicant
                                :t.email.application-revoked/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-revoked/subject-to-handler
                                :t.email.application-revoked/message-to-handler)))

(defmethod event-to-emails :application.event/closed [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.application-closed/subject-to-applicant
                                :t.email.application-closed/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-closed/subject-to-handler
                                :t.email.application-closed/message-to-handler)))

(defmethod event-to-emails :application.event/returned [event application]
  (concat (emails-to-recipients [(:application/applicant application)]
                                event application
                                :t.email.application-returned/subject-to-applicant
                                :t.email.application-returned/message-to-applicant)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.application-returned/subject-to-handler
                                :t.email.application-returned/message-to-handler)))

(defmethod event-to-emails :application.event/licenses-added [event application]
  (emails-to-recipients (application-util/applicant-and-members application)
                        event application
                        :t.email.application-licenses-added/subject
                        :t.email.application-licenses-added/message))

(defmethod event-to-emails :application.event/submitted [event application]
  (concat (emails-to-recipients [(:application/applicant application)]
                                event application
                                :t.email.application-submitted/subject-to-applicant
                                :t.email.application-submitted/message-to-applicant)
          (if (= (:event/time event)
                 (:application/first-submitted application))
            (emails-to-recipients (handlers application)
                                  event application
                                  :t.email.application-submitted/subject-to-handler
                                  :t.email.application-submitted/message-to-handler)
            (emails-to-recipients (handlers application)
                                  event application
                                  :t.email.application-resubmitted/subject-to-handler
                                  :t.email.application-resubmitted/message-to-handler))))

(defmethod event-to-emails :application.event/review-requested [event application]
  (emails-to-recipients (:application/reviewers event)
                        event application
                        :t.email.review-requested/subject
                        :t.email.review-requested/message))

(defmethod event-to-emails :application.event/reviewed [event application]
  (emails-to-recipients (handlers application)
                        event application
                        :t.email.reviewed/subject
                        :t.email.reviewed/message))

(defmethod event-to-emails :application.event/remarked [event application]
  (emails-to-recipients (concat (handlers application)
                                (when (:application/public event)
                                  ;; no need to email members on non-actionable things
                                  [(:application/applicant application)]))
                        event application
                        :t.email.remarked/subject
                        :t.email.remarked/message))

(defmethod event-to-emails :application.event/decided [event application]
  (emails-to-recipients (handlers application)
                        event application
                        :t.email.decided/subject
                        :t.email.decided/message))

(defmethod event-to-emails :application.event/decision-requested [event application]
  (emails-to-recipients (:application/deciders event)
                        event application
                        :t.email.decision-requested/subject
                        :t.email.decision-requested/message))

(defmethod event-to-emails :application.event/member-added [event application]
  ;; TODO email to applicant? email to handler?
  (emails-to-recipients [(:application/member event)]
                        event application
                        :t.email.member-added/subject
                        :t.email.member-added/message))

(defmethod event-to-emails :application.event/member-invited [event application]
  (with-language (:default-language env)
    (fn []
      [{:to (:email (:application/member event))
        :subject (text-format :t.email.member-invited/subject
                              (:name (:application/member event))
                              (application-util/get-applicant-name application)
                              (format-application-for-email application)
                              (invitation-link (:invitation/token event)))
        :body (str
               (text-format :t.email.member-invited/message
                            (:name (:application/member event))
                            (application-util/get-applicant-name application)
                            (format-application-for-email application)
                            (invitation-link (:invitation/token event)))
               (text :t.email/regards)
               (text :t.email/footer))}])))

(defmethod event-to-emails :application.event/reviewer-invited [event application]
  (with-language (:default-language env)
    (fn []
      [{:to (:email (:application/reviewer event))
        :subject (text-format :t.email.reviewer-invited/subject
                              (:name (:application/reviewer event))
                              (application-util/get-applicant-name application)
                              (format-application-for-email application)
                              (invitation-link (:invitation/token event)))
        :body (str
               (text-format :t.email.reviewer-invited/message
                            (:name (:application/reviewer event))
                            (application-util/get-applicant-name application)
                            (format-application-for-email application)
                            (invitation-link (:invitation/token event)))
               (text :t.email/regards)
               (text :t.email/footer))}])))

(defmethod event-to-emails :application.event/decider-invited [event application]
  (with-language (:default-language env)
    (fn []
      [{:to (:email (:application/decider event))
        :subject (text-format :t.email.decider-invited/subject
                              (:name (:application/decider event))
                              (application-util/get-applicant-name application)
                              (format-application-for-email application)
                              (invitation-link (:invitation/token event)))
        :body (str
               (text-format :t.email.decider-invited/message
                            (:name (:application/decider event))
                            (application-util/get-applicant-name application)
                            (format-application-for-email application)
                            (invitation-link (:invitation/token event)))
               (text :t.email/regards)
               (text :t.email/footer))}])))

(defmethod event-to-emails :application.event/applicant-changed [event application]
  (concat (emails-to-recipients (application-util/applicant-and-members application)
                                event application
                                :t.email.applicant-changed/subject-to-member
                                :t.email.applicant-changed/message-to-member)
          (emails-to-recipients (other-handlers event application)
                                event application
                                :t.email.applicant-changed/subject-to-handler
                                :t.email.applicant-changed/message-to-handler)))

(defmethod event-to-emails :application.event/expiration-notifications-sent [event application]
  (vec
   (for [recipient (application-util/applicant-and-members application)]
     (with-language (:language (user-settings/get-user-settings (:userid recipient)))
       (fn []
         {:to-user (:userid recipient)
          :subject (text-format :t.email.application-expiration-notification/subject-to-member
                                (application-util/get-member-name recipient)
                                (format-application-for-email application)
                                (localize-utc-date (:last-activity event))
                                (localize-utc-date (:expires-on event))
                                (link-to-application (:application/id application)))
          :body (str
                 (text-format :t.email.application-expiration-notification/message-to-member
                              (application-util/get-member-name recipient)
                              (format-application-for-email application)
                              (localize-utc-date (:last-activity event))
                              (localize-utc-date (:expires-on event))
                              (link-to-application (:application/id application)))
                 (text :t.email/regards)
                 (text :t.email/footer))})))))

;; TODO member-joined?

(defn handler-reminder-email [lang handler applications]
  (with-language lang
    (fn []
      (when (not (empty? applications))
        (let [list (->> applications
                        (map (fn [application]
                               (text-format :t.email.handler-reminder/application
                                            (format-application-for-email application)
                                            (application-util/get-member-name (:application/applicant application)))))
                        (str/join "\n"))]
          {:to-user (:userid handler)
           :subject (text :t.email.handler-reminder/subject)
           :body (text-format :t.email.handler-reminder/message
                              (application-util/get-member-name handler)
                              list
                              (str (:public-url env) "actions"))})))))

(defn reviewer-reminder-email [lang reviewer applications]
  (with-language lang
    (fn []
      (when (not (empty? applications))
        (let [list (->> applications
                        (map (fn [application]
                               (text-format :t.email.reviewer-reminder/application
                                            (format-application-for-email application)
                                            (application-util/get-member-name (:application/applicant application)))))
                        (str/join "\n"))]
          {:to-user (:userid reviewer)
           :subject (text :t.email.reviewer-reminder/subject)
           :body (text-format :t.email.reviewer-reminder/message
                              (application-util/get-member-name reviewer)
                              list
                              (str (:public-url env) "actions"))})))))

(defn workflow-handler-invitation-email [lang invitation workflow]
  (with-language lang
    (fn []
      (when workflow
        {:to (:invitation/email invitation)
         :subject (text-format :t.email.workflow-handler-invitation/subject
                               (:invitation/name invitation)
                               (get-in invitation [:invitation/invited-by :name])
                               (:title workflow)
                               (invitation-link (:invitation/token invitation)))
         :body (str
                (text-format :t.email.workflow-handler-invitation/message
                             (:invitation/name invitation)
                             (get-in invitation [:invitation/invited-by :name])
                             (:title workflow)
                             (invitation-link (:invitation/token invitation)))
                (text :t.email/regards)
                (text :t.email/footer))}))))

