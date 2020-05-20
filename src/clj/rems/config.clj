(ns rems.config
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [mount.core :refer [defstate]]
            [rems.application.commands :as commands]
            [rems.application.events :as events]
            [rems.json :as json])
  (:import [java.io FileNotFoundException]
           [org.joda.time Period]))

(defn- file-sibling [file sibling-name]
  (.getPath (io/file (.getParentFile (io/file file))
                     sibling-name)))

(defn- get-file [config key]
  (if-let [file (get config key)]
    (if (.isFile (io/file file))
      file
      (throw (FileNotFoundException. (str "the file specified in " key " does not exist: " file))))))

(defn load-external-theme [config]
  (if-let [file (get-file config :theme-path)]
    (merge-maps config
                {:theme (source/from-file file)
                 :theme-static-resources (file-sibling file "public")})
    config))

(defn- parse-config [config]
  (-> config
      (update :email-retry-period #(Period/parse %))
      (update :disable-commands (partial mapv keyword))))

(deftest test-parse-config
  (is (= {:foo 1
          :email-retry-period (Period/days 20)
          :disable-commands [:application.command/close :application.command/reject]}
         (parse-config
          {:foo 1
           :email-retry-period "P20d"
           :disable-commands ["application.command/close" "application.command/reject"]}))))

(def known-config-keys
  (set (keys (load-config :resource "config-defaults.edn"))))

;; if we start doing more thorough validation, could use a schema instead
(defn- validate-config [config]
  (when-let [url (:public-url config)]
    (assert (.endsWith url "/")
            (str ":public-url should end with /:" (pr-str url))))
  (assert (contains? (set (:languages config)) (:default-language config))
          (str ":default-language should be one of :languages: "
               (pr-str (select-keys config [:default-language :languages]))))
  (when-let [invalid-commands (seq (remove (set commands/command-names) (:disable-commands config)))]
    (log/warn "Unrecognized values in :disable-commands :" (pr-str invalid-commands))
    (log/warn "Supported-values:" (pr-str commands/command-names)))
  (doseq [target (:event-notification-targets config)]
    (when-let [invalid-events (seq (remove (set events/event-types) (:event-types target)))]
      (log/warn "Unrecognized event types in event notification target"
                (pr-str target)
                ":"
                (pr-str invalid-events))
      (log/warn "Supported event types:" (pr-str events/event-types))))
  (when-let [invalid-keys (seq (remove known-config-keys (keys config)))]
    (log/warn "Unrecognized config keys: " (pr-str invalid-keys)))
  config)

(defstate env :start (-> (load-config :resource "config-defaults.edn"
                                      ;; If the "rems.config" system property is not defined, the :file parameter will
                                      ;; fall back to using the "conf" system property (hard-coded in cprop).
                                      ;; If neither system property is defined, the :file parameter is silently ignored.
                                      :file (System/getProperty "rems.config"))
                         (load-external-theme)
                         (parse-config)
                         (validate-config)))

(defn get-oidc-config [oidc-domain]
  (-> (http/get
       (str "https://"
            oidc-domain
            "/.well-known/openid-configuration"))
      (:body)
      (json/parse-string)))

(defstate oidc-configuration :start (when-let [oidc-domain (env :oidc-domain)]
                                      (get-oidc-config oidc-domain)))
