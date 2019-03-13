(ns rems.poller.email
  "Sending emails based on application events."
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [postal.core :as postal]
            [rems.config :refer [env]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.users :as users]
            [rems.text :refer [text text-format with-language]]
            [rems.json :as json]
            [rems.util :as util]
            [rems.workflow.dynamic :as dynamic]))

;;; Mapping events to emails

;; TODO link to application?
;; TODO list of resources?
;; TODO use real name when addressing user?

(defmulti ^:private event-to-emails-impl
  (fn [event _application] (:event/type event)))

(defmethod event-to-emails-impl :default [_event _application]
  [])

(defmethod event-to-emails-impl :application.event/approved [event application]
  (vec
   (for [member (:members application)] ;; applicant is a member
     {:to-user (:userid member)
      :subject (text :t.email.application-approved/subject)
      :body (text-format :t.email.application-approved/message
                         (:userid member)
                         (:id application))})))

(defmethod event-to-emails-impl :application.event/rejected [event application]
  (vec
   (for [member (:members application)] ;; applicant is a member
     {:to-user (:userid member)
      :subject (text :t.email.application-rejected/subject)
      :body (text-format :t.email.application-rejected/message
                         (:userid member)
                         (:id application))})))

(defmethod event-to-emails-impl :application.event/comment-requested [event _application]
  (vec
   (for [commenter (:application/commenters event)]
     {:to-user commenter
      :subject (text :t.email.comment-requested/subject)
      :body (text-format :t.email.comment-requested/message
                         commenter
                         (:event/actor event)
                         (:application/id event))})))

(defmethod event-to-emails-impl :application.event/decision-requested [event _application]
  (vec
   (for [decider (:application/deciders event)]
     {:to-user decider
      :subject (text :t.email.decision-requested/subject)
      :body (text-format :t.email.decision-requested/message
                         decider
                         (:event/actor event)
                         (:application/id event))})))

(defmethod event-to-emails-impl :application.event/commented [event application]
  (vec
   (for [handler (get-in application [:workflow :handlers])]
     {:to-user handler
      :subject (text :t.email.commented/subject)
      :body (text-format :t.email.commented/message
                         handler
                         (:event/actor event)
                         (:application/id event))})))

(defmethod event-to-emails-impl :application.event/decided [event application]
  (vec
   (for [handler (get-in application [:workflow :handlers])]
     {:to-user handler
      :subject (text :t.email.decided/subject)
      :body (text-format :t.email.decided/message
                         handler
                         (:event/actor event)
                         (:application/id event))})))

(defmethod event-to-emails-impl :application.event/member-added [event _application]
  ;; TODO email to applicant? email to handler?
  [{:to-user (:userid (:application/member event))
    :subject (text :t.email.member-added/subject)
    :body (text-format :t.email.member-added/message
                       (:userid (:application/member event))
                       (:application/id event))}])

(defmethod event-to-emails-impl :application.event/member-invited [event _application]
  [{:to (:email (:application/member event))
    :subject (text :t.email.member-invited/subject)
    :body (text-format :t.email.member-invited/message
                       (:email (:application/member event))
                       ;; TODO the actual invitation link!
                       (:invitation/token event))}])

;; TODO member-joined?

(defn event-to-emails [event]
  (when-let [app-id (:application/id event)]
    ;; TODO use api-get-application-v2 or similar
    (event-to-emails-impl event (applications/get-application-state app-id))))

;;; Generic poller infrastructure

;; these can be moved to rems.poller once we have multiple pollers
(defn get-poller-state [name-kw]
  (or (json/parse-string (:state (db/get-poller-state {:name (name name-kw)})))
      {:last-processed-event-id 0}))

(defn set-poller-state! [name-kw state]
  (db/set-poller-state! {:name (name name-kw) :state (json/generate-string state)})
  nil)

(defn run-event-poller [name-kw process-event!]
  ;; This isn't thread-safe but ScheduledThreadPoolExecutor guarantees exclusion
  (let [prev-state (get-poller-state name-kw)
        events (applications/get-dynamic-application-events-since (:last-processed-event-id prev-state))]
    (log/info name-kw "running with state" (pr-str prev-state))
    (try
      (doseq [e events]
        (try
          (log/info name-kw "processing event" (:event/id e))
          (process-event! e)
          (set-poller-state! name-kw {:last-processed-event-id (:event/id e)})
          (catch Throwable t
            (throw (Exception. (str name-kw " processing event " (pr-str e)) t)))))
      (catch Throwable t
        (log/error t)))
    (log/info name-kw "finished")))

(deftest test-run-event-poller-error-handling
  (let [events (atom [])
        add-event! #(swap! events conj %)
        ids-to-fail (atom #{})
        processed (atom [])
        process-event! (fn [event]
                         (when (contains? @ids-to-fail (:event/id event))
                           (throw (Error. "BOOM")))
                         (swap! processed conj event))
        poller-state (atom {:last-processed-event-id 0})
        run #(run-event-poller :test process-event!)]
    (with-redefs [applications/get-dynamic-application-events-since (fn [id] (filterv #(< id (:event/id %)) @events))
                  get-poller-state (fn [_] @poller-state)
                  set-poller-state! (fn [_ state] (reset! poller-state state))]
      (testing "no events, nothing should happen"
        (run)
        (is (= {:last-processed-event-id 0} @poller-state))
        (is (= [] @processed)))
      (testing "add a few events, process them"
        (add-event! {:event/id 1})
        (add-event! {:event/id 3})
        (run)
        (is (= {:last-processed-event-id 3} @poller-state))
        (is (= [{:event/id 1} {:event/id 3}] @processed)))
      (testing "add a failing event"
        (add-event! {:event/id 5})
        (add-event! {:event/id 7})
        (add-event! {:event/id 9})
        (reset! ids-to-fail #{7})
        (reset! processed [])
        (run)
        (is (= {:last-processed-event-id 5} @poller-state))
        (is (= [{:event/id 5}] @processed)))
      (testing "run again after failure, nothing should happen"
        (reset! processed [])
        (run)
        (is (= {:last-processed-event-id 5} @poller-state))
        (is (= [] @processed)))
      (testing "fix failure, run"
        (reset! ids-to-fail #{})
        (run)
        (is (= {:last-processed-event-id 9} @poller-state))
        (is (= [{:event/id 7} {:event/id 9}] @processed))))))

;;; Email poller

;; You can test email sending by:
;;
;; 1. running mailhog: docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog
;; 2. adding {:mail-from "rems@example.com" :smtp-host "localhost" :smtp-port 1025} to dev-config.edn
;; 3. generating some emails
;;    - you can reset the email poller state with (set-poller-state! :rems.poller.email/poller nil)
;; 4. open http://localhost:8025 in your browser to view the emails

(defn send-email! [email-spec]
  (let [host (:smtp-host env)
        port (:smtp-port env)]
    (if (and host port)
      (let [email (assoc email-spec
                         :from (:mail-from env)
                         :body (str (:body email-spec)
                                    (text :t.email/footer))
                         :to (or (:to email-spec)
                                 (util/get-user-mail
                                  (users/get-user-attributes
                                   (:to-user email-spec)))))]
        ;; TODO check that :to is set
        (log/info "sending email:" (pr-str email))
        (postal/send-message {:host host :port port} email))
      (do
        (log/info "pretending to send email:" (pr-str email-spec))))))

(defn run []
  (run-event-poller ::poller (fn [event]
                               (with-language :en
                                 #(doseq [mail (event-to-emails event)]
                                    (send-email! mail))))))

(mount/defstate email-poller
  :start (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
           (.scheduleWithFixedDelay run 10 10 java.util.concurrent.TimeUnit/SECONDS))
  :stop (doto email-poller
          (.shutdown)
          (.awaitTermination 60 java.util.concurrent.TimeUnit/SECONDS)))

(comment
  (mount/start #{#'email-poller})
  (mount/stop #{#'email-poller}))
