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
            [rems.scheduler :as scheduler])
  (:import [javax.mail.internet InternetAddress]
           [org.joda.time Duration Period]))

(defn- event-to-emails [event]
  (when-let [app-id (:application/id event)]
    (template/event-to-emails (rems.application.model/enrich-event event users/get-user (constantly nil))
                              (applications/get-application app-id))))

(defn- enqueue-email! [email]
  (outbox/put! {:outbox/type :email
                :outbox/email email
                :outbox/deadline (-> (time/now) (.plus ^Period (:email-retry-period env)))}))

(defn generate-event-emails! [new-events]
  (doseq [event new-events
          email (event-to-emails event)]
    (enqueue-email! email)))

(defn generate-handler-reminder-emails! []
  (doseq [email (->> (workflow/get-handlers)
                     (map (fn [handler]
                            (let [lang (:language (user-settings/get-user-settings (:userid handler)))
                                  apps (todos/get-todos (:userid handler))]
                              (template/handler-reminder-email lang handler apps))))
                     (remove nil?))]
    (enqueue-email! email)))

(defn generate-reviewer-reminder-emails! []
  (doseq [email (->> (applications/get-users-with-role :reviewer)
                     (map users/get-user)
                     (map (fn [reviewer]
                            (let [lang (:language (user-settings/get-user-settings (:userid reviewer)))
                                  apps (->> (todos/get-todos (:userid reviewer))
                                            (map #(= :waiting-for-your-review (:application/todo %))))]
                              (template/reviewer-reminder-email lang reviewer apps))))
                     (remove nil?))]
    (enqueue-email! email)))

(defn- render-invitation-template [invitation]
  (let [lang (:default-language env)] ; we don't know the preferred languages here since there is no user
    (cond (:invitation/workflow invitation)
          (let [workflow (workflow/get-workflow (get-in invitation [:invitation/workflow :workflow/id]))]
            (assert workflow "Can't send invitation, missing workflow")
            (template/workflow-handler-invitation-email lang invitation workflow)))))

(defn generate-invitation-emails! [invitations]
  (doseq [invitation invitations
          :when (not (:invitation/sent invitation))
          :let [email (render-invitation-template invitation)]]
    (invitation/mail-sent! (:invitation/id invitation))
    (enqueue-email! email)))

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
      (log/info "sending email:" (pr-str email))
      (cond
        to-error
        (do
          (log/warn "failed address validation:" to-error)
          (str "failed address validation: " to-error))

        (not (and (:host smtp) (:port smtp)))
        (do
          (log/info "no smtp server configured, only pretending to send email")
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

(defn try-send-emails! []
  (doseq [email (outbox/get-due-entries :email)]
    (if-let [error (send-email! (:outbox/email email))]
      (let [email (outbox/attempt-failed! email error)]
        (when (not (:outbox/next-attempt email))
          (log/warn "all attempts to send email" (:outbox/id email) "failed")))
      (outbox/attempt-succeeded! (:outbox/id email)))))

(mount/defstate email-poller
  :start (scheduler/start! "email-poller" try-send-emails! (Duration/standardSeconds 10))
  :stop (scheduler/stop! email-poller))

(comment
  (mount/start #{#'email-poller})
  (mount/stop #{#'email-poller}))
