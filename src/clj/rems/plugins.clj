(ns rems.plugins
  "REMS can be extended with plugins in certain extension points.

  The plugins are loaded dynamically from external
  code found in the specified file.

  See also docs/plugins.md"
  (:require [clj-http.client]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [mount.core :as mount]
            [nextjournal.beholder :as beholder]
            [rems.common.util :refer [getx index-by]]
            [rems.config :refer [env]]
            [rems.markdown :as md]
            [sci.core :as sci]))

;; logging is difficult to expose so we wrap it

(def ^:dynamic *logging-ctx*)

(defn log-wrapper
  "Wraps the `clojure.tools.logging` macros and presents helper functions for plugins.

  The keys `extension-point-id` and `plugin-id` are automatically included in the logs from the
  `*logging-ctx*`."
  [level]
  (fn [x & args]
    (let [{:keys [extension-point-id plugin-id]} *logging-ctx*]
      (if (instance? Throwable x)
        (log/log level x (apply print-str extension-point-id plugin-id "-" args))
        (log/log level (apply print-str extension-point-id plugin-id "-" x args))))))





;;; SCI

(def sci-ctx
  "Default SCI context for plugins."
  (delay
    (sci/init {:namespaces {'rems.config {'env env}
                            'clojure.string (sci/copy-ns clojure.string (sci/create-ns 'clojure.string))
                            'clj-http.client (sci/copy-ns clj-http.client (sci/create-ns 'clj-http.client))
                            'clojure.tools.logging {'trace (log-wrapper :trace)
                                                    'debug (log-wrapper :debug)
                                                    'info (log-wrapper :info)
                                                    'warn (log-wrapper :warn)
                                                    'error (log-wrapper :error)
                                                    'fatal (log-wrapper :fatal)}

                            'rems.common.util {'getx getx}}})))

;; make sure SCI printing goes to stdout
(sci/alter-var-root sci/out (constantly *out*))
(sci/alter-var-root sci/err (constantly *err*))




;;; Plugins

;; TODO: use common cache architecture?
(def plugin-configs (atom {}))
(def plugin-ctx-cache (atom {}))

(defn- load-plugin-configs! []
  (reset! plugin-configs
          (->> env
               :plugins
               (index-by [:id]))))

(defn- get-plugin-config [plugin-id]
  (getx @plugin-configs plugin-id))

(defn- get-plugins-at [extension-point-id]
  (->> (get-in env [:extension-points extension-point-id])
       (mapv get-plugin-config)))

(defn- load-plugin [plugin-config]
  (let [plugin-filename (getx plugin-config :filename)
        _ (log/info "Loading plugin" (:id plugin-config) "from" (pr-str plugin-filename))
        ctx (-> @sci-ctx sci/fork)]
    (-> plugin-filename
        io/reader
        line-seq
        md/strip-to-clj-content
        str/join
        (->> (sci/eval-string* ctx))) ; evaluate plugin (defs) into context
    ctx))

(defn- load-plugins! []
  (reset! plugin-ctx-cache {})
  (doseq [plugin-config (vals @plugin-configs)
          :let [plugin-id (getx plugin-config :id)]]
    (swap! plugin-ctx-cache assoc plugin-id (load-plugin plugin-config))))


(defn- plugin-files-changed [{:keys [path] :as _event}]
  (let [filename (.toString (.toAbsolutePath path))
        _ (log/debug "Changed plugin file" filename)
        changed-plugins (for [plugin-config (:plugins env)
                              :when (.endsWith path (:filename plugin-config))]
                          plugin-config)]
    (doseq [plugin-config changed-plugins
            :let [plugin-id (:id plugin-config)]]
      (swap! plugin-ctx-cache assoc plugin-id (load-plugin plugin-config)))))

(defn- remove-filename [filename]
  (.getParent (io/file filename)))




;;; Plugins (re-)loaded as files change

(mount/defstate plugin-loader
  :start (let [plugin-paths (->> env
                                 :plugins
                                 (mapv :filename)
                                 (mapv remove-filename)
                                 (into #{}))]
           (log/info "Loading plugins from:" (pr-str plugin-paths))
           ;; NB: while plugins can be reloaded
           ;; the plugin configs are read only at mount
           ;; start as they are part of the config
           (load-plugin-configs!)
           (load-plugins!)
           (apply beholder/watch plugin-files-changed plugin-paths))
  :stop (beholder/stop plugin-loader))





;;; Internal entrypoint

(defn- run-plugin
  "Run the plugin from `plugin-config` passing in `data` at `extension-point-id` (context used for logging).

  The plugin runs through SCI with some context injected (data, config, env, log etc.)"
  [symbol-name extension-point-id data plugin-config]
  (let [plugin-id (getx plugin-config :id)]
    (log/debug "Running plugin" plugin-id)
    (let [plugin (getx @plugin-ctx-cache plugin-id)
          ctx (-> plugin
                  sci/fork
                  (sci/merge-opts {:namespaces {'user {'config plugin-config
                                                       'data data}}}))]
      (binding [*logging-ctx* {:extension-point-id extension-point-id
                               :plugin-id plugin-id}]
        (sci/eval-string* ctx (str "(" symbol-name " config data)"))))))





;;; Public API

(defn process
  "Executes the configured plugins of `extension-point-id` to process the given `data`.

  Each plugin gets a turn with the same original `data`.

  The plugins will be executed in turn, in the same order as they have been configured.
  The plugins are expected to do something side-effectful, like a HTTP request.

  Each plugin should return a sequence of errors. The first non-empty result will
  be returned (the first errors encountered) or `nil` (no errors). This would
  will cause the processing to be re-tried later so you should prefer idempotent
  implementations and external services."
  [extension-point-id data]
  (let [plugin-configs (get-plugins-at extension-point-id)]
    (some (fn [plugin-config]
            (seq (run-plugin "process" extension-point-id data plugin-config))) ; can return n problems
          plugin-configs)))

(defn transform
  "Reduces the configured plugins of `extension-point-id` to transform the given `data`.

  Each plugin should return the same or modified `data`.

  The plugins will be executed in turn, in the same order as they have been configured. So
  the return value of the one plugin will be the `data` of the next.

  Returns whatever the last plugin returns."
  [extension-point-id data]
  (let [plugin-configs (get-plugins-at extension-point-id)]
    (reduce (partial run-plugin "transform" extension-point-id)
            data
            plugin-configs)))

(defn validate
  "Validates `data` with the plugins of `extension-point-id`.

  The plugins will be executed in turn, in the same order as they have been configured.

  Each plugin should return a sequence of errors. The first non-empty result will
  be returned (the first errors encountered) or `nil` (no errors)."
  [extension-point-id data]
  (let [plugin-configs (get-plugins-at extension-point-id)]
    (some (fn [plugin-config]
            (seq (run-plugin "validate" extension-point-id data plugin-config))) ; can return n problems
          plugin-configs)))

