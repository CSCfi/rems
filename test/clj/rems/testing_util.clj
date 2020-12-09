(ns rems.testing-util
  (:require [clj-time.core :as time]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.organizations :as organizations])
  (:import (ch.qos.logback.classic Level Logger)
           (com.google.common.io MoreFiles RecursiveDeleteOption)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (org.joda.time DateTimeZone DateTimeUtils)
           (org.slf4j LoggerFactory)))

(defn utc-fixture [f]
  (let [old (DateTimeZone/getDefault)]
    (DateTimeZone/setDefault DateTimeZone/UTC)
    (f)
    (DateTimeZone/setDefault old)))

(defn with-fixed-time [date f]
  (DateTimeUtils/setCurrentMillisFixed (.getMillis date))
  (try
    (f)
    (finally
      (DateTimeUtils/setCurrentMillisSystem))))

(defn fixed-time-fixture [date]
  (fn [f]
    (with-fixed-time date f)))

(defn suppress-logging [^String logger-name]
  (fn [f]
    (let [^Logger logger (LoggerFactory/getLogger logger-name)
          original-level (.getLevel logger)]
      (.setLevel logger Level/OFF)
      (f)
      (.setLevel logger original-level))))

(defn create-temp-dir []
  (.toFile (Files/createTempDirectory (.toPath (io/file "target"))
                                      "test"
                                      (make-array FileAttribute 0))))

(defn delete-recursively [dir]
  (MoreFiles/deleteRecursively (.toPath (io/file dir))
                               (into-array [RecursiveDeleteOption/ALLOW_INSECURE])))

(defmacro with-user [user & body]
  `(binding [context/*user* (users/get-raw-user-attributes ~user)
             context/*roles* (set/union (roles/get-roles ~user)
                                        (organizations/get-all-organization-roles ~user)
                                        (applications/get-all-application-roles ~user))]
     ~@body))
