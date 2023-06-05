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

;;; SCI

(defn log-wrapper
  "Wraps the `clojure.tools.logging` macros and presents helper functions for plugins.

  The `extension-point-id` and `plugin-id` are automatically included in the logs."
  [extension-point-id plugin-id level]
  (fn [x & args]
    (if (instance? Throwable x)
      (log/log level x (apply print-str extension-point-id plugin-id "-" args))
      (log/log level (apply print-str extension-point-id plugin-id "-" x args)))))

(def sci-ctx
  "Default SCI context for plugins."
  (delay
    (sci/init {:namespaces {'rems.config {'env env}
                            'clojure.string (sci/copy-ns clojure.string (sci/create-ns 'clojure.string))
                            'clj-http.client (sci/copy-ns clj-http.client (sci/create-ns 'clj-http.client))

                            'user {'getx getx}}})))

;; make sure SCI printing goes to stdout
(sci/alter-var-root sci/out (constantly *out*))
(sci/alter-var-root sci/err (constantly *err*))




;; TODO: use common cache architecture?
(def plugin-configs (atom {}))
(def plugin-cache (atom {}))




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

(defn- wrap-log-ns [extension-point-id plugin-id]
  {'log {'trace (log-wrapper extension-point-id plugin-id :trace)
         'debug (log-wrapper extension-point-id plugin-id :debug)
         'info (log-wrapper extension-point-id plugin-id :info)
         'warn (log-wrapper extension-point-id plugin-id :warn)
         'error (log-wrapper extension-point-id plugin-id :error)
         'fatal (log-wrapper extension-point-id plugin-id :fatal)}})

(defn- load-plugin [plugin-config]
  (let [plugin-filename (getx plugin-config :filename)
        _ (log/info "Loading plugin" (:id plugin-config) "from" (pr-str plugin-filename))
        ctx (-> @sci-ctx
                sci/fork
                (sci/merge-opts {:namespaces (wrap-log-ns :loading plugin-config)}))]
    (-> plugin-filename
        io/reader
        line-seq
        md/strip-to-clj-content
        str/join
        (->> (sci/eval-string* ctx))) ; evaluate plugin (defs) into context
    ctx))

(defn- load-plugins! []
  (reset! plugin-cache {})
  (doseq [plugin-config (vals @plugin-configs)
          :let [plugin-id (getx plugin-config :id)]]
    (swap! plugin-cache assoc plugin-id (load-plugin plugin-config))))


(defn- plugins-files-changed [{:keys [path] :as _event}]
  (let [filename (.toString (.toAbsolutePath path))
        _ (log/debug "Changed plugin file" filename)
        changed-plugins (for [plugin-config (:plugins env)
                              :when (.endsWith path (:filename plugin-config))]
                          plugin-config)]
    (doseq [plugin-config changed-plugins
            :let [plugin-id (:id plugin-config)]]
      (swap! plugin-cache assoc plugin-id (load-plugin plugin-config)))))

(defn- remove-filename [filename]
  (.getParent (io/file filename)))

(mount/defstate plugin-loader
  :start (let [plugin-paths (->> env
                                 :plugins
                                 (mapv :filename)
                                 (mapv remove-filename)
                                 (into #{}))]
           (log/info "Loading plugins from:" (pr-str plugin-paths))
           ;; NB: plugin configs are read only at mount start
           (load-plugin-configs!)
           (load-plugins!)
           (apply beholder/watch plugins-files-changed plugin-paths))
  :stop (beholder/stop plugin-loader))





(defn- run-plugin
  "Run the plugin from `plugin-config` passing in `data` at `extension-point-id` (context used for logging).

  The plugin runs through SCI with some context injected (data, config, env, log etc.)"
  [symbol-name extension-point-id data plugin-config]
  (let [plugin-id (getx plugin-config :id)]
    (log/debug "Running plugin" plugin-id)
    (let [plugin (getx @plugin-cache plugin-id)
          ctx (-> plugin
                  sci/fork
                  (sci/merge-opts {:namespaces (merge (wrap-log-ns extension-point-id plugin-config)
                                                      {'user {'config plugin-config
                                                              'data data}})}))]
      (sci/eval-string* ctx (str "(" symbol-name " config data)")))))





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

