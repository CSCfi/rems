(ns rems.testing-util
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.test]
            [kaocha.hierarchy]
            [kaocha.testable]
            [rems.context :as context]
            [rems.db.applications]
            [rems.db.roles]
            [rems.db.users]
            [rems.db.organizations]
            [rems.locales]
            [rems.text])
  (:import [ch.qos.logback.classic Level]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [org.joda.time DateTime DateTimeZone DateTimeUtils]
           [org.slf4j LoggerFactory]))

(defn utc-fixture [f]
  (let [old (DateTimeZone/getDefault)]
    (DateTimeZone/setDefault DateTimeZone/UTC)
    (f)
    (DateTimeZone/setDefault old)))

(defmacro with-fixed-time [^DateTime date & body]
  `(try
     (DateTimeUtils/setCurrentMillisFixed (.getMillis ~date))
     ~@body
     (finally
       (DateTimeUtils/setCurrentMillisSystem))))

(defn fixed-time-fixture [date]
  (fn [f]
    (with-fixed-time date
      (f))))

(defmacro with-suppress-logging [logger-names & body]
  `(let [loggers# (for [logger-name# (flatten (list ~logger-names))
                        :let [logger# (LoggerFactory/getLogger logger-name#)]]
                    {:logger logger#
                     :original-level (.getLevel logger#)})]
     (run! #(.setLevel (:logger %) Level/OFF) loggers#)
     ~@body
     (run! #(.setLevel (:logger %) (:original-level %)) loggers#)))

(defn suppress-logging-fixture [& logger-names]
  (fn [f]
    (with-suppress-logging logger-names
      (f))))

(defn create-temp-dir []
  (.toFile (Files/createTempDirectory (.toPath (io/file "target"))
                                      "test"
                                      (make-array FileAttribute 0))))

(defmacro with-user [user & body]
  `(binding [context/*user* (rems.db.users/get-user ~user)
             context/*roles* (set/union (rems.db.roles/get-roles ~user)
                                        (rems.db.organizations/get-all-organization-roles ~user)
                                        (rems.db.applications/get-all-application-roles ~user))]
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

(defmacro with-translations [translations & body]
  `(try
     (with-redefs [rems.locales/translations ~translations]
       (rems.text/reset-cached-tr!) ; ensure next call gets new cache
       ~@body)
     (finally ; clean up stale cache
       (rems.text/reset-cached-tr!))))

(defn- get-kaocha-test-id []
  (when-let [testable kaocha.testable/*current-testable*]
    (when (kaocha.hierarchy/leaf? testable)
      (:kaocha.testable/id testable))))

(defn get-current-test []
  (or (some-> (get-kaocha-test-id) symbol)
      (some-> clojure.test/*testing-vars* first symbol)))

(defn get-current-test-name []
  (if-let [current-test (get-current-test)]
    (name current-test)
    "unknown"))
