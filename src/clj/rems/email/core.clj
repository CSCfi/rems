(ns rems.email.core
  "Sending emails based on application events."
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [postal.core :as postal]
            [rems.application.model]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.users :as users]
            [rems.email.outbox :as email-outbox]
            [rems.email.template :as template]
            [rems.scheduler :as scheduler])
  (:import [javax.mail.internet InternetAddress]
           [org.joda.time Duration Period]))

(defn- event-to-emails [event]
  (when-let [app-id (:application/id event)]
    (template/event-to-emails (rems.application.model/enrich-event event users/get-user (constantly nil))
                              (applications/get-unrestricted-application app-id))))

(defn generate-emails! [new-events]
  (doseq [event new-events
          email (event-to-emails event)]
    (email-outbox/put! {:outbox/email email
                        :outbox/deadline (-> (time/now) (.plus ^Period (:email-retry-period env)))})))

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
  (let [host (:smtp-host env)
        port (:smtp-port env)
        email (assoc email-spec
                     :from (:mail-from env)
                     :to (or (:to email-spec)
                             (:email
                              (users/get-user
                               (:to-user email-spec)))))
        to-error (validate-address (:to email))]
    (when (:to email)
      (log/info "sending email:" (pr-str email))
      (cond
        to-error
        (do
          (log/warn "failed address validation:" to-error)
          (str "failed address validation: " to-error))

        (not (and host port))
        (do
          (log/info "no smtp server configured, only pretending to send email")
          nil)

        :else
        (try
          (postal/send-message {:host host :port port} email)
          nil
          (catch Throwable e ; e.g. email address does not exist
            (log/warn e "failed sending email:" (pr-str email))
            (str "failed sending email: " e)))))))

(defn try-send-emails! []
  (doseq [email (email-outbox/get-emails {:due-now? true})]
    (if-let [error (send-email! (:outbox/email email))]
      (let [email (email-outbox/attempt-failed! email error)]
        (when (not (:outbox/next-attempt email))
          (log/warn "all attempts to send email" (:outbox/id email) "failed")))
      (email-outbox/attempt-succeeded! (:outbox/id email)))))

(mount/defstate email-poller
  :start (scheduler/start! try-send-emails! (Duration/standardSeconds 10))
  :stop (scheduler/stop! email-poller))

(comment
  (mount/start #{#'email-poller})
  (mount/stop #{#'email-poller}))
