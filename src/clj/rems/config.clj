(ns rems.config
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [cprop.core :refer [load-config]]
            [cprop.source :as source]
            [cprop.tools :refer [merge-maps]]
            [medley.core :refer [update-existing]]
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

(defn load-keys [config]
  (merge config
         (when-let [key-file (get-file config :ga4gh-visa-private-key)]
           {:ga4gh-visa-private-key (json/parse-string (slurp key-file))})
         (when-let [key-file (get-file config :ga4gh-visa-public-key)]
           {:ga4gh-visa-public-key (json/parse-string (slurp key-file))})))

(defn- parse-config [config]
  (-> config
      (update :email-retry-period #(Period/parse %))
      (update-existing :extra-pages (partial mapv #(update-existing % :roles set)))
      (update :disable-commands (partial mapv keyword))))

(deftest test-parse-config
  (is (= {:foo 1
          :email-retry-period (Period/days 20)
          :disable-commands [:application.command/close :application.command/reject]}
         (parse-config
          {:foo 1
           :email-retry-period "P20d"
           :disable-commands ["application.command/close" "application.command/reject"]}))))

(defn known-config-keys []
  (set (keys (load-config :resource "config-defaults.edn"))))

(defn env-config-keys []
  (set (keys (source/from-env))))

(defn system-properties-keys []
  (set (keys (source/from-system-props))))

;; if we start doing more thorough validation, could use a schema instead
(defn- validate-config [config]
  (when-let [url (:public-url config)]
    (assert (.endsWith url "/")
            (str ":public-url should end with /:" (pr-str url))))
  (assert (contains? (set (:languages config)) (:default-language config))
          (str ":default-language should be one of :languages: "
               (pr-str (select-keys config [:default-language :languages]))))
  (when (:oidc-domain config)
    (log/warn ":oidc-domain is deprecated, prefer :oidc-metadata-url"))
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
  (when-let [unrecognized-keys (seq (->> (keys config)
                                         (remove (known-config-keys))
                                         (remove (system-properties-keys)) ; don't complain about system properties
                                         (remove (env-config-keys))))] ; don't complain about environment
    (log/warn "Unrecognized config keys: " (pr-str unrecognized-keys)))
  config)

(defstate env :start (-> (load-config :resource "config-defaults.edn"
                                      ;; If the "rems.config" system property is not defined, the :file parameter will
                                      ;; fall back to using the "conf" system property (hard-coded in cprop).
                                      ;; If neither system property is defined, the :file parameter is silently ignored.
                                      :file (System/getProperty "rems.config")
                                      :merge [(source/from-system-props) ; load all properties and env (otherwise we can only override keys present already)
                                              (source/from-env)])
                         (load-external-theme)
                         (load-keys)
                         (parse-config)
                         (validate-config)))

(defn- oidc-metadata-url []
  (or (:oidc-metadata-url env)
      (when-let [domain (env :oidc-domain)]
        (str "https://"
             domain
             "/.well-known/openid-configuration"))))

(defn fetch-and-check-oidc-config [url]
  (try
    (let [resp (http/get url {:accept :json})
          body (:body resp)
          content-type (get-in resp [:headers "Content-Type"] "")]
      (assert (.startsWith content-type "application/json")
              (str "Expected application/json content type, got " content-type))
      (let [configuration (json/parse-string body)]
        (assert (contains? configuration :authorization_endpoint)
                "OIDC configuration did not contain \"authorization_endpoint\"")
        (assert (contains? configuration :token_endpoint)
                "OIDC configuration did not contain \"token_endpoint\"")
        (assert (contains? configuration :issuer)
                "OIDC configuration did not contain \"issuer\"")
        configuration))
    (catch Throwable t
      (throw (Error. (str "Failed to fetch valid OIDC configuration from " url) t)))))

(defstate oidc-configuration :start (when-let [url (oidc-metadata-url)]
                                      (fetch-and-check-oidc-config url)))
