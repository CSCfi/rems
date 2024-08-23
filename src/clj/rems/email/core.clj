(ns rems.email.core
  "Sending emails based on application events."
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [postal.core :as postal]
            [rems.service.todos :as todos]
            [rems.service.workflow :as workflow]
            [rems.application.model]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.invitation :as invitation]
            [rems.db.outbox :as outbox]
            [rems.db.user-settings :as user-settings]
            [rems.db.users :as users]
            [rems.email.template :as template]
            [rems.scheduler :as scheduler]
            [clojure.string :as str])
  (:import [javax.mail.internet InternetAddress]
           [org.joda.time Duration Period]))

(defn- event-to-emails [event]
  ;; performance optimization:
  ;; avoid get application if no email should be sent for this event
  (when-not (contains? #{:application.event/created
                         :application.event/draft-saved}
                       (:event/type event))
    (when-let [app-id (:application/id event)]
      (template/event-to-emails (rems.application.model/enrich-event event users/get-user (constantly nil))
                                (applications/get-application app-id)))))

(defn generate-event-emails! [new-events]
  (let [deadline (-> (time/now) (.plus ^Period (:email-retry-period env)))]
    (->> (for [event new-events
               email (event-to-emails event)]
           {:outbox/type :email
            :outbox/email email
            :outbox/deadline deadline})
         outbox/puts!)
    nil)) ; no new events

(defn generate-handler-reminder-outbox [handler deadline]
  (let [userid (:userid handler)
        lang (:language (user-settings/get-user-settings userid))
        apps (todos/get-todos userid)
        email (template/handler-reminder-email lang handler apps)]

    (when email
      {:outbox/type :email
       :outbox/email email
       :outbox/deadline deadline})))

(defn generate-handler-reminder-emails! []
  (let [deadline (-> (time/now) (.plus ^Period (:email-retry-period env)))]
    (->> (workflow/get-handlers)
         (keep #(generate-handler-reminder-outbox % deadline))
         outbox/puts!)))

(defn generate-reviewer-reminder-outbox [reviewer deadline]
  (let [userid (:userid reviewer)
        lang (:language (user-settings/get-user-settings userid))
        apps (->> (todos/get-todos userid)
                  (filter (comp #{:waiting-for-your-review} :application/todo)))
        email (template/reviewer-reminder-email lang reviewer apps)]

    (when email
      {:outbox/type :email
       :outbox/email email
       :outbox/deadline deadline})))

(defn generate-reviewer-reminder-emails! []
  (let [deadline (-> (time/now) (.plus ^Period (:email-retry-period env)))]
    (->> (applications/get-users-with-role :reviewer)
         (map users/get-user)
         (keep #(generate-reviewer-reminder-outbox % deadline))
         outbox/puts!)))

(defn- render-invitation-template [invitation]
  (let [lang (:default-language env)] ; we don't know the preferred languages here since there is no user
    (cond (:invitation/workflow invitation)
          (let [workflow (workflow/get-workflow (get-in invitation [:invitation/workflow :workflow/id]))]
            (assert workflow "Can't send invitation, missing workflow")
            (template/workflow-handler-invitation-email lang invitation workflow)))))

(defn- mark-invitations-sent! [invitations]
  (doseq [invitation invitations]
    (invitation/mail-sent! (:invitation/id invitation))))

(defn generate-invitation-emails! [invitations]
  (let [deadline (-> (time/now) (.plus ^Period (:email-retry-period env)))]
    (->> (for [invitation invitations
               :when (not (:invitation/sent invitation))
               :let [email (render-invitation-template invitation)]]
           {:outbox/type :email
            :outbox/email email
            :outbox/deadline deadline})
         outbox/puts!)
    (mark-invitations-sent! invitations)))

;;; Email poller

;; You can test email sending by:
;;
;; 1. running mailhog: docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog
;; 2. adding {:mail-from "rems@example.com" :smtp-host "localhost" :smtp-port 1025} to dev-config.edn
;; 3. generating some emails
;; 4. open http://localhost:8025 in your browser to view the emails

(defn- validate-address
  "Returns nil for a valid email address, string message for an invalid one."
  [email]
  (try
    (InternetAddress. email)
    nil
    (catch Throwable t
      (str "Invalid address "
           (pr-str email)
           ": "
           t))))

(deftest test-validate-address
  (is (nil? (validate-address "valid@example.com")))
  (is (string? (validate-address "")))
  (is (string? (validate-address nil)))
  (is (string? (validate-address "test@test_example.com"))))

(defn send-email! [email-spec]
  (let [smtp (merge {:host (:smtp-host env)
                     :port (:smtp-port env)}
                    (:smtp env)) ; can override host and port
        email (assoc email-spec
                     :from (:mail-from env)
                     :to (or (:to email-spec)
                             (:notification-email (user-settings/get-user-settings (:to-user email-spec)))
                             (:email (users/get-user (:to-user email-spec))))
                     ;; https://tools.ietf.org/html/rfc3834
                     ;; postal turns extra keys into headers
                     "Auto-Submitted" "auto-generated")
        to-error (validate-address (:to email))]
    (when (and (:body email) (:to email))
      (when (or (not (:dev env))
                (not (str/includes? (:body email "") "Performance test application")))
        (log/info "sending email:" (pr-str email)))
      (cond
        to-error
        (do
          (log/warn "failed address validation:" to-error)
          (str "failed address validation: " to-error))

        (not (and (:host smtp) (:port smtp)))
        (do
          (when (or (not (:dev env))
                    (not (str/includes? (:body email "") "Performance test application")))
            (log/info "no smtp server configured, only pretending to send email"))
          nil)

        :else
        (try
          (postal/send-message (merge smtp
                                      {:debug (true? (:smtp-debug env))}
                                      (when-let [timeout (:smtp-connectiontimeout env)]
                                        {"mail.smtp.connectiontimeout" (str timeout)
                                         "mail.smtps.connectiontimeout" (str timeout)})
                                      (when-let [timeout (:smtp-writetimeout env)]
                                        {"mail.smtp.writetimeout" (str timeout)
                                         "mail.smtps.writetimeout" (str timeout)})
                                      (when-let [timeout (:smtp-timeout env)]
                                        {"mail.smtp.timeout" (str timeout)
                                         "mail.smtps.timeout" (str timeout)}))
                               email)
          nil
          (catch Throwable e ; e.g. email address does not exist
            (log/warn e "failed sending email:" (pr-str email))
            (str "failed sending email: " e)))))))

(defn handle-send-error! [email error]
  (if (str/includes? error "javax.mail.internet.AddressException") ; e.g. illegal character in address
    ;; give up immediately
    (let [email (outbox/attempt-failed-fatally! email error)]
      (log/warn "all attempts to send email " (:outbox/id email) "failed")
      ::giving-up-after-fatal-error)

    ;; maybe keep trying
    (let [email (outbox/attempt-failed! email error)]
      (if (:outbox/next-attempt email)
        ::failed-but-will-retry-again

        (do
          (log/warn "all attempts to send email " (:outbox/id email) "failed")
          ::giving-up-after-all-attempts)))))

(defn try-send-emails! []
  (log/debug "Trying to send emails")
  (let [due-emails (outbox/get-due-entries :email)]
    (log/debug (str "Emails due: " (count due-emails)))
    (doseq [email due-emails]
      (if-let [error (send-email! (:outbox/email email))]
        (handle-send-error! email error)

        (outbox/attempt-succeeded! (:outbox/id email))))))

(mount/defstate email-poller
  :start (scheduler/start! "email-poller" try-send-emails! (Duration/standardSeconds 10))
  :stop (scheduler/stop! email-poller))

(comment
  (mount/start #{#'email-poller})
  (mount/stop #{#'email-poller}))
