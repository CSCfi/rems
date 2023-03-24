(ns rems.scheduler
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit ExecutorService]
           [org.joda.time Duration]))

(defn ^ExecutorService start! [name f ^Duration interval]
  (let [interval-millis (.getMillis interval)
        task (fn []
               (try
                 (log/debug "Scheduler starting work")
                 (f)
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
