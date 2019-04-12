(ns rems.poller.common
  "Generic infrastructure for event pollers"
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.json :as json]))

(defn get-poller-state [name-kw]
  (or (json/parse-string (:state (db/get-poller-state {:name (str name-kw)})))
      {:last-processed-event-id 0}))

(defn set-poller-state! [name-kw state]
  (db/set-poller-state! {:name (str name-kw) :state (json/generate-string state)})
  nil)

(defn run-event-poller [name-kw process-event!]
  ;; This isn't thread-safe but ScheduledThreadPoolExecutor guarantees exclusion
  (let [prev-state (get-poller-state name-kw)
        events (applications/get-dynamic-application-events-since (:last-processed-event-id prev-state))]
    (log/debug name-kw "running with state" (pr-str prev-state))
    (try
      (doseq [e events]
        (try
          ;; TODO: add proper monitoring for pollers and caches
          (log/info name-kw "processing event" (:event/id e))
          (process-event! e)
          (set-poller-state! name-kw {:last-processed-event-id (:event/id e)})
          (catch Throwable t
            (throw (Exception. (str name-kw " processing event " (pr-str e)) t)))))
      (catch Throwable t
        (log/error t)))
    (log/debug name-kw "finished")))

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
