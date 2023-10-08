(ns rems.scheduler
  (:require [clj-time.core :as time-core]
            [clj-time.format :as time-format]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [clojure.test :refer [deftest is]])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit ExecutorService]
           [org.joda.time Duration Interval]))

(defn- seconds-since-midnight [hour-minute-string]
  (->> hour-minute-string
       (time-format/parse (time-format/formatters :hour-minute))
       (time-core/interval (time-core/epoch))
       time-core/in-seconds
       time-core/seconds))

(defn- time-on-day [dt hour-minute-string]
  (time-core/plus
   (time-core/with-time-at-start-of-day dt)
   (seconds-since-midnight hour-minute-string)))

(defn- intervals-on-day [dt hour-minute-strings]
  (for [[start end] hour-minute-strings]
    (time-core/interval (time-on-day dt start)
                        (time-on-day dt end))))

(defn- time-within-intervals? [dt intervals]
  (some? (some #(time-core/within? ^Interval % dt) intervals)))

(deftest test-time-within-intervals?
  (let [buzy-hours (intervals-on-day (time-core/date-time 2023 10 7)
                                     [["07:00" "11:00"]
                                      ["12:00" "17:00"]])
        at (fn [& [h m s]] (time-core/date-time 2023 10 7 (or h 0) (or m 0) (or s 0)))]
    (is (not (time-within-intervals? (at 0) buzy-hours)))
    (is (not (time-within-intervals? (at 6 59 59) buzy-hours)))
    (is (time-within-intervals? (at 7) buzy-hours))
    (is (time-within-intervals? (at 10 59 59) buzy-hours))
    (is (not (time-within-intervals? (at 11) buzy-hours)))
    (is (not (time-within-intervals? (at 11 15) buzy-hours)))
    (is (time-within-intervals? (at 12) buzy-hours))
    (is (not (time-within-intervals? (at 17) buzy-hours)))
    (is (not (time-within-intervals? (at 22) buzy-hours)))))

(defn ^ExecutorService start! [name f ^Duration interval & [opts]]
  (let [interval-millis (.getMillis interval)
        task (fn []
               (try
                 (log/debug "Scheduler starting work")
                 (let [now (time-core/now)
                       buzy-hours (intervals-on-day now (:buzy-hours opts))
                       buzy-hour? (time-within-intervals? now buzy-hours)]
                   (if buzy-hour?
                     (log/debug "Scheduler skipping because of buzy hour" (:buzy-hours opts))
                     (f)))
                 (log/debug "Scheduler is done")
                 (catch InterruptedException e
                   (.interrupt (Thread/currentThread))
                   (log/info e "Scheduler shutting down"))
                 (catch Throwable t ; prevents suppressing subsequent executions
                   (log/error t "Internal error" (with-out-str (when-let [data (ex-data t)]
                                                                 (pprint data)))))))
        factory (proxy [java.util.concurrent.ThreadFactory] []
                  (newThread [r]
                    (let [thread (.newThread (java.util.concurrent.Executors/defaultThreadFactory) r)]
                      (.setName thread (str name "-" (.getName thread)))
                      thread)))]

    (doto (ScheduledThreadPoolExecutor. 1 factory)
      (.scheduleWithFixedDelay task interval-millis interval-millis TimeUnit/MILLISECONDS))))

(defn stop! [^ExecutorService scheduler]
  (.shutdownNow scheduler)
  (when-not (.awaitTermination scheduler 5 TimeUnit/MINUTES)
    (throw (IllegalStateException. "did not terminate"))))
