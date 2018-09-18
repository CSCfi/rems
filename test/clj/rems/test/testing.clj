(ns rems.test.testing
  (:import (org.slf4j LoggerFactory)
           (ch.qos.logback.classic Level Logger)))

(defn suppress-logging [^String logger-name]
  (fn [f]
    (let [^Logger logger (LoggerFactory/getLogger logger-name)
          original-level (.getLevel logger)]
      (.setLevel logger Level/OFF)
      (f)
      (.setLevel logger original-level))))
