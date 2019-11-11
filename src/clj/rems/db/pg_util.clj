(ns rems.db.pg-util
  (:require [clojure.test :refer [deftest is]])
  (:import [java.util Calendar]
           [org.joda.time Period Duration]
           [org.postgresql.util PGInterval]))

;; TODO: the database layer could be made to support PGInterval<->Duration conversions automatically

(defn ^Duration pg-interval->joda-duration [^PGInterval interval]
  (let [calendar (doto (Calendar/getInstance)
                   (.setTimeInMillis 0))]
    (.add interval calendar)
    (Duration. (.getTimeInMillis calendar))))

(defn ^PGInterval joda-duration->pg-interval [^Duration duration]
  (doto (PGInterval.)
    (.setSeconds (/ (.getMillis duration) 1000.0))))

(deftest test-pg-interval->joda-duration
  (is (= (Duration. 0) (pg-interval->joda-duration (PGInterval.))))
  (is (= (.toStandardDuration (Period. 0 0 0 3 4 5 6 7)) ; months and years vary in length, so they cannot be converted to duration
         (pg-interval->joda-duration (PGInterval. 0 0 3 4 5 6.007)))))

(deftest test-joda-duration->pg-interval
  (is (= (PGInterval.) (joda-duration->pg-interval (Duration. 0))))
  (is (= (PGInterval. "1 secs") (joda-duration->pg-interval (Duration. 1000))))
  (is (= (PGInterval. "0.001 secs") (joda-duration->pg-interval (Duration. 1))))
  (is (= (PGInterval. "3600 secs") (joda-duration->pg-interval (Duration/standardHours 1)))))
