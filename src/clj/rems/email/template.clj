(ns rems.email.template
  (:require [clojure.string :as str]
            [rems.application.model :as model]
            [rems.common.application-util :as application-util]
            [rems.common.util :refer [getx]]
            [rems.config :refer [env]]
            [rems.context :as context]
            [rems.db.user-settings]
            [rems.permissions :as permissions]
            [rems.text :refer [localize-user localize-utc-date text text-no-fallback text-format-map with-language]]))

;;; Mapping events to emails

;; TODO list of resources?
;; TODO use real name when addressing user?

(defn url [] (or (not-empty (:external-frontend-url env)) (:public-url env)))

;; move this to a util namespace if its needed somewhere else
(defn- link-to-application [application-id]
  (str (url) "application/" application-id))

(defn- invitation-link [token]
  (prn "generating invitation link")
  (prn (url))
  (prn env)
  (str (url) "accept-invitation?token=" token))

(defn- format-application-for-email [application]
  (str
   (case (getx env :application-id-column)
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

(defn- apply-event-privacy [event application userid]
  (cond
    (contains? (permissions/user-permissions application userid) :see-everything)
    event

    (get-in application [:application/workflow :workflow/anonymize-handling])
    (-> [event]
        (model/anonymize-users-in-events (model/get-handling-users application))
        first)

    :else
    event))

(defmulti event-to-emails
  (fn [event _application] (:event/type event)))

(defmethod event-to-emails :default [_event _application]
  [])

(defn- emails-to-recipients [recipients event application subject-text body-text]
  (vec
   (for [recipient recipients
         :let [email (with-language (:language (rems.db.user-settings/get-user-settings (:userid recipient)))
                       (when (and body-text
                                  (not (str/blank? (text-no-fallback body-text))))
                         (let [event (apply-event-privacy event application (:userid recipient))
                               params {:applicant (application-util/get-applicant-name application)
                                       :application-id (format-application-for-email application)
                                       :application-url (link-to-application (:application/id event))
                                       :catalogue-items (resources-for-email application)
                                       :event-actor (localize-user (:event/actor-attributes event))
                                       :recipient (application-util/get-member-name recipient)}]
                           {:to-user (:userid recipient)
                            :subject (text-format-map subject-text
                                                      params
                                                      [:recipient :event-actor :application-id :applicant :catalogue-items :application-url])
                            :body (str
                                   (text-format-map body-text
                                                    params
                                                    [:recipient :event-actor :application-id :applicant :catalogue-items :application-url])
                                   (text :t.email/regards)
                                   (text :t.email/footer))})))]
         :when email]
     email)))

(defmethod event-to-emails :application.event/created [_event _application]
  (assert false "performance optimization, not called, no emails for created"))

(defmethod event-to-emails :application.event/saved [_event _application]
  (assert false "performance optimization, not called, no emails for saved"))

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
  (emails-to-recipients (concat (other-handlers event application)
                                (when (:event/public event)
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
    (let [invited-user (:application/member event)
          params {:applicant (application-util/get-applicant-name application)
                  :application-id (format-application-for-email application)
                  :invitation-url (invitation-link (:invitation/token event))
                  :recipient (application-util/get-member-name invited-user)}]
      [{:to (:email invited-user)
        :subject (text-format-map :t.email.member-invited/subject
                                  params
                                  [:recipient :applicant :application-id :invitation-url])
        :body (str
               (text-format-map :t.email.member-invited/message
                                params
                                [:recipient :applicant :application-id :invitation-url])
               (text :t.email/regards)
               (text :t.email/footer))}])))

(defmethod event-to-emails :application.event/reviewer-invited [event application]
  (let [invited-user (:application/reviewer event)
        params {:applicant (application-util/get-applicant-name application)
                :application-id (format-application-for-email application)
                :invitation-url (invitation-link (:invitation/token event))
                :recipient (application-util/get-member-name invited-user)}]
    (with-language (:default-language env)
      [{:to (:email invited-user)
        :subject (text-format-map :t.email.reviewer-invited/subject
                                  params
                                  [:recipient :applicant :application-id :invitation-url])
        :body (str
               (text-format-map :t.email.reviewer-invited/message
                                params
                                [:recipient :applicant :application-id :invitation-url])
               (text :t.email/regards)
               (text :t.email/footer))}])))

(defmethod event-to-emails :application.event/decider-invited [event application]
  (with-language (:default-language env)
    (let [invited-user (:application/decider event)
          params {:applicant (application-util/get-applicant-name application)
                  :application-id (format-application-for-email application)
                  :invitation-url (invitation-link (:invitation/token event))
                  :recipient (application-util/get-member-name invited-user)}]
      [{:to (:email invited-user)
        :subject (text-format-map :t.email.decider-invited/subject
                                  params
                                  [:recipient :applicant :application-id :invitation-url])
        :body (str
               (text-format-map :t.email.decider-invited/message
                                params
                                [:recipient :applicant :application-id :invitation-url])
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
   (let [last-activity (:event/time (application-util/get-last-applying-user-event application))]
     (for [recipient (application-util/applicant-and-members application)
           :let [params {:application-id (format-application-for-email application)
                         :application-url (link-to-application (:application/id event))
                         :expires-on (localize-utc-date (:application/expires-on event))
                         :last-activity (localize-utc-date last-activity)
                         :recipient (application-util/get-member-name recipient)}]]
       (with-language (:language (rems.db.user-settings/get-user-settings (:userid recipient)))
         {:to-user (:userid recipient)
          :subject (text-format-map :t.email.application-expiration-notification/subject-to-member
                                    params
                                    [:recipient :application-id :last-activity :expires-on :application-url])
          :body (str
                 (text-format-map :t.email.application-expiration-notification/message-to-member
                                  params
                                  [:recipient :application-id :last-activity :expires-on :application-url])
                 (text :t.email/regards)
                 (text :t.email/footer))})))))

;; TODO member-joined?

(defn handler-reminder-email [lang handler applications]
  (with-language lang
    (when (seq applications)
      (let [formatted-applications (for [app applications
                                         :let [params {:applicant (application-util/get-applicant-name app)
                                                       :application-id (format-application-for-email app)}]]
                                     (text-format-map :t.email.handler-reminder/application
                                                      params
                                                      [:application-id :applicant]))
            params {:actions-url (str (url) "actions")
                    :applications (str/join "\n" formatted-applications)
                    :recipient (application-util/get-member-name handler)}]
        {:to-user (:userid handler)
         :subject (text :t.email.handler-reminder/subject)
         :body (text-format-map :t.email.handler-reminder/message
                                params
                                [:recipient :applications :actions-url])}))))

(defn reviewer-reminder-email [lang reviewer applications]
  (with-language lang
    (when (seq applications)
      (let [formatted-applications (for [app applications
                                         :let [params {:applicant (application-util/get-applicant-name app)
                                                       :application-id (format-application-for-email app)}]]
                                     (text-format-map :t.email.reviewer-reminder/application
                                                      params
                                                      [:application-id :applicant]))
            params {:actions-url (str (url) "actions")
                    :applications (str/join "\n" formatted-applications)
                    :recipient (application-util/get-member-name reviewer)}]
        {:to-user (:userid reviewer)
         :subject (text :t.email.reviewer-reminder/subject)
         :body (text-format-map :t.email.reviewer-reminder/message
                                params
                                [:recipient :applications :actions-url])}))))

(defn workflow-handler-invitation-email [lang invitation workflow]
  (with-language lang
    (when workflow
      (let [params {:invited-by (get-in invitation [:invitation/invited-by :name])
                    :invitation-url (invitation-link (:invitation/token invitation))
                    :recipient (:invitation/name invitation)
                    :workflow (:title workflow)}]
        {:to (:invitation/email invitation)
         :subject (text-format-map :t.email.workflow-handler-invitation/subject
                                   params
                                   [:recipient :invited-by :workflow :invitation-url])
         :body (str
                (text-format-map :t.email.workflow-handler-invitation/message
                                 params
                                 [:recipient :invited-by :workflow :invitation-url])
                (text :t.email/regards)
                (text :t.email/footer))}))))
