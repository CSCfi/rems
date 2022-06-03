(ns rems.testing-util
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.organizations :as organizations])
  (:import [ch.qos.logback.classic Level Logger]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.joda.time DateTimeZone DateTimeUtils]
           [org.slf4j LoggerFactory]))

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

(defmacro with-user [user & body]
  `(binding [context/*user* (users/get-raw-user-attributes ~user)
             context/*roles* (set/union (roles/get-roles ~user)
                                        (organizations/get-all-organization-roles ~user)
                                        (applications/get-all-application-roles ~user))]
     ~@body))

(defmacro with-fake-login-users
  "Runs the body with the given `users` as id-data and user-info for fake login.

  NB: both datasets are therefore identical and that is different than usual,
  but that shouldn't matter in practice"
  [users & body]
  `(with-redefs [rems.auth.fake-login/get-fake-users (constantly (keys ~users))
                 rems.auth.fake-login/get-fake-id-data (fn [username#] (get ~users username#))
                 rems.auth.fake-login/get-fake-user-info (fn [username#] (get ~users username#))
                 rems.auth.fake-login/get-fake-user-descriptions (constantly [{:group "Test Users"
                                                                               :users ~(vec (for [[k v] users]
                                                                                              {:userid k
                                                                                               :description (pr-str v)}))}])]
     ~@body))

