(ns rems.poller.email
  "Sending emails based on application events."
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rems.json :as json]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.workflow.dynamic :as dynamic]))

;;; Mapping events to emails

;; TODO pass in application state
(defmulti ^:private event-to-emails-impl
  (fn [event _application] (:event/type event)))

(defmethod event-to-emails-impl :default [_event _application]
  [])

(defmethod event-to-emails-impl :application.event/approved [event application]
  (for [member (:members application)] ;; applicant is a member
    {:to (:userid member)
     :body (str "application " (:application/id event) " has been approved")}))

(defmethod event-to-emails-impl :application.event/rejected [event application]
  (for [member (:members application)] ;; applicant is a member
    {:to (:userid member)
     :body (str "application " (:application/id event) " has been rejected")}))

(defmethod event-to-emails-impl :application.event/comment-requested [event _application]
  (for [c (:application/commenters event)]
    {:to c
     :body (str "please comment on " (:application/id event))}))

(defmethod event-to-emails-impl :application.event/decision-requested [event _application]
  (for [c (:application/deciders event)]
    {:to c
     :body (str "please decide " (:application/id event))}))

(defmethod event-to-emails-impl :application.event/commented [event application]
  (for [h (get-in application [:workflow :handlers])]
    {:to h
     :body (str "comment by " (:event/actor event)  ": " (:application/comment event))}))

(defmethod event-to-emails-impl :application.event/decided [event application]
  (for [h (get-in application [:workflow :handlers])]
    {:to h
     :body (str "decision by " (:event/actor event)  ": " (:application/decision event))}))

(defmethod event-to-emails-impl :application.event/member-added [event _application]
  [{:to (:userid (:application/member event))
    :body "you've been added"}])

(defmethod event-to-emails-impl :application.event/member-invited [event _application]
  [{:to (:email (:application/member event))
    :body "invitation email"}])

(defn event-to-emails [event]
  (when-let [app-id (:application/id event)]
    ;; TODO use api-get-application-v2 or similar
    (event-to-emails-impl event (applications/get-application-state app-id))))

(deftest test-event-to-emails-impl
  (let [events [{:application/id 7
                 :event/type :application.event/created
                 :event/actor "applicant"
                 :workflow/type :workflow/dynamic
                 :workflow.dynamic/handlers #{"handler" "assistant"}}
                {:application/id 7
                 :event/type :application.event/submitted
                 :event/actor "applicant"}
                {:application/id 7
                 :event/type :application.event/member-invited
                 :event/actor "applicant"
                 :application/member {:name "Some Body" :email "somebody@example.com"}
                 :invitation/toke "abc"}
                {:application/id 7
                 :event/type :application.event/comment-requested
                 :application/request-id "r1"
                 :application/commenters ["commenter1" "commenter2"]}
                {:application/id 7
                 :event/type :application.event/member-joined
                 :event/actor "somebody"}
                {:application/id 7
                 :event/type :application.event/commented
                 :event/actor "commenter2"
                 :application/request-id "r1"
                 :application/comment ["this is a comment"]}
                {:application/id 7
                 :event/type :application.event/member-added
                 :event/actor "handler"
                 :application/member {:userid "member"}}
                {:application/id 7
                 :event/type :application.event/decision-requested
                 :event/actor "assistant"
                 :application/request-id "r2"
                 :application/deciders ["decider"]}
                {:application/id 7
                 :event/type :application.event/decided
                 :event/actor "decider"
                 :application/decision :approved}
                {:application/id 7
                 :event/type :application.event/approved
                 :event/actor "handler"}]
        application (dynamic/apply-events nil events)]
    (is (= [[] ;; created
            [] ;; submitted
            [{:to "somebody@example.com" :body "invitation email"}]
            [{:to "commenter1" :body "please comment on 7"}
             {:to "commenter2" :body "please comment on 7"}]
            [] ;; member-joined
            [{:to "handler"
              :body "comment by commenter2: [\"this is a comment\"]"}
             {:to "assistant"
              :body "comment by commenter2: [\"this is a comment\"]"}]
            [{:to "member" :body "you've been added"}]
            [{:to "decider" :body "please decide 7"}]
            [{:to "handler" :body "decision by decider: :approved"}
             {:to "assistant" :body "decision by decider: :approved"}]
            [{:to "applicant" :body "application 7 has been approved"}
             {:to "somebody", :body "application 7 has been approved"}
             {:to "member", :body "application 7 has been approved"}]]
           (mapv #(event-to-emails-impl % application) events)))))

;;; Sending emails

(defn send-email! [email-spec]
  ;; just a stub for now
  (log/info "email:" (pr-str email-spec)))

;;; Generic poller infrastructure

;; these can be moved to rems.poller once we have multiple pollers
(defn get-poller-state [name-kw]
  (or (json/parse-string (:state (db/get-poller-state {:name (name name-kw)})))
      {:event/id -1}))

(defn set-poller-state! [name-kw state]
  (db/set-poller-state! {:name (name name-kw) :state (json/generate-string state)})
  nil)

(defn run-event-poller [name-kw process-event!]
  (let [prev-state (get-poller-state name-kw)
        events (applications/get-dynamic-application-events-since (:event/id prev-state))]
    (log/info name-kw "running with state" (pr-str prev-state))
    (when-not (empty? events)
      (try
        (doseq [e events]
          (try
            (log/info name-kw "processing event" (:event/id e))
            (process-event! e)
            (set-poller-state! name-kw {:event/id (:event/id e)})
            (catch Throwable t
              (throw (Exception. (str name-kw " processing event " (pr-str e)) t)))))
        (catch Throwable t
          (log/error t))))
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
        poller-state (atom {:event/id -1})
        run #(run-event-poller :test process-event!)]
    (with-redefs [applications/get-dynamic-application-events-since (fn [id] (filterv #(< id (:event/id %)) @events))
                  get-poller-state (fn [_] @poller-state)
                  set-poller-state! (fn [_ state] (reset! poller-state state))]
      (testing "no events, nothing should happen"
        (run)
        (is (= {:event/id -1} @poller-state))
        (is (= [] @processed)))
      (testing "add a few events, process them"
        (add-event! {:event/id 1})
        (add-event! {:event/id 3})
        (run)
        (is (= {:event/id 3} @poller-state))
        (is (= [{:event/id 1} {:event/id 3}] @processed)))
      (testing "add a failing event"
        (add-event! {:event/id 5})
        (add-event! {:event/id 7})
        (add-event! {:event/id 9})
        (reset! ids-to-fail #{7})
        (reset! processed [])
        (run)
        (is (= {:event/id 5} @poller-state))
        (is (= [{:event/id 5}] @processed)))
      (testing "run again after failure, nothing should happen"
        (reset! processed [])
        (run)
        (is (= {:event/id 5} @poller-state))
        (is (= [] @processed)))
      (testing "fix failure, run"
        (reset! ids-to-fail #{})
        (run)
        (is (= {:event/id 9} @poller-state))
        (is (= [{:event/id 7} {:event/id 9}] @processed))))))

;;; Email poller

(defn run []
  (run-event-poller ::poller (fn [event]
                               (doseq [mail (event-to-emails event)]
                                 (send-email! mail)))))

(mount/defstate email-poller
  :start (doto (java.util.concurrent.ScheduledThreadPoolExecutor. 1)
           (.scheduleWithFixedDelay run 10 10 java.util.concurrent.TimeUnit/SECONDS))
  :stop (doto email-poller
          (.shutdown)
          (.awaitTermination 60 java.util.concurrent.TimeUnit/SECONDS)))

(comment
  (mount/start #{#'email-poller})
  (mount/stop #{#'email-poller}))
