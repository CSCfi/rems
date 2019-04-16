(ns rems.scheduler
  (:import [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit ExecutorService]
           [org.joda.time Duration]))

(defn ^ExecutorService start! [f ^Duration interval]
  (let [interval-millis (.getMillis interval)]
    (doto (ScheduledThreadPoolExecutor. 1)
      (.scheduleWithFixedDelay f interval-millis interval-millis TimeUnit/MILLISECONDS))))

(defn stop! [^ExecutorService scheduler]
  (.shutdownNow scheduler)
  (when-not (.awaitTermination scheduler 5 TimeUnit/MINUTES)
    (throw (IllegalStateException. "did not terminate"))))
