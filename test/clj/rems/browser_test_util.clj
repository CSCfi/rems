(ns rems.browser-test-util
  "Browser test utils.

  NB: Don't use etaoin directly but wrap it to functions that don't need the driver to be passed."
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.stacktrace]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.rpl.specter :refer [ALL select]]
            [etaoin.api :as et]
            [medley.core :refer [assoc-some]]
            [rems.common.util :refer [conj-vec getx parse-int]]
            [rems.config :refer [env]]
            [rems.json :as json]
            [rems.testing-util :refer [get-current-test-name]]
            [rems.util :refer [ensure-empty-directory!]]
            [slingshot.slingshot :refer [try+]])
  (:import [java.net SocketException]))

;;; test setup

(defn create-test-context []
  {:url "http://localhost:3001/"
   :mode :test
   :seed "circle"
   :reporting-dir (io/file "browsertest-errors")
   :accessibility-report-dir (io/file "browsertest-accessibility-report")
   :download-dir (io/file "browsertest-downloads")})

(defonce global-test-context (atom (create-test-context)))

(def ^:dynamic *test-context*)

(defn test-ctx
  ([] (if (bound? #'rems.browser-test-util/*test-context*)
        *test-context*
        global-test-context))
  ([& ks]
   (get-in @(test-ctx) ks)))

(defn get-driver [] (test-ctx :driver))
(defn get-server-url [] (test-ctx :url))
(defn get-seed [] (test-ctx :seed))

;; test data uses separate key path to avoid conflicts with configuration data

(defn reset-context! [] (swap! (test-ctx) dissoc :test-data))

(defn context-get [k] (test-ctx :test-data k))
(defn context-getx [k] (getx (test-ctx :test-data) k))
(defn context-update! [f & args] (apply swap! (test-ctx) update :test-data f args))
(defn context-assoc! [k v & kvs] (apply context-update! assoc k v kvs))
(defn context-dissoc! [k & ks] (apply context-update! dissoc k ks))

(defn- ensure-empty-directories! []
  (ensure-empty-directory! (test-ctx :reporting-dir))
  (ensure-empty-directory! (test-ctx :accessibility-report-dir))
  (ensure-empty-directory! (test-ctx :download-dir)))

(defn downloaded-files
  ([] (for [file (.listFiles (test-ctx :download-dir))]
        file))
  ([name-or-regex] (if (string? name-or-regex)
                     (let [f (io/file (test-ctx :download-dir) name-or-regex)]
                       (when (.exists f) [f]))
                     (for [file (.listFiles (test-ctx :download-dir))
                           :when (re-matches name-or-regex (.getName file))]
                       file))))

(defn delete-downloaded-files! [name-or-regex]
  (let [files (if (string? name-or-regex)
                [(io/file (test-ctx :download-dir) name-or-regex)]
                (for [file (.listFiles (test-ctx :download-dir))
                      :when (re-matches name-or-regex (.getName file))]
                  file))]
    (doseq [file files]
      (.delete file))))

(defn- mod-nth [coll i]
  (nth coll (mod (int i) (count coll))))

(defn- random-seed []
  (let [t (System/currentTimeMillis)]
    (str (mod-nth ["red" "green" "blue" "magenta" "cyan" "yellow" "white" "black" "brown" "pink"] (/ t 60000))
         " "
         (mod-nth ["amusing" "comic" "funny" "laughable" "hilarious" "witty" "jolly" "silly" "ludicrous" "wacky"] (/ t 1000))
         " "
         (mod-nth ["leopard" "gorilla" "turtle" "orangutan" "elephant" "saola" "vaquita" "tiger" "rhino" "pangolin"] (mod t 123)))))

(defn- enable-downloads! [driver]
  (et/execute {:driver driver
               :method :post
               :path [:session (:session driver) "chromium/send_command"]
               :data {:cmd "Page.setDownloadBehavior"
                      :params {:behavior "allow"
                               :downloadPath (.getAbsolutePath (test-ctx :download-dir))}}}))

(defn- reset-window-size!
  "Sets window size big enough to show the whole page in the screenshots."
  [driver]
  (et/set-window-rect driver {:width 1400 :height 7000}))

(defn- init-session! [driver]
  (doto driver
    (enable-downloads!)
    (reset-window-size!)))

(def ^:private driver-defaults
  {:args ["--lang=en-US"
          "--disable-search-engine-choice-screen"] ; https://stackoverflow.com/a/78800001
   :prefs {:intl.accept_languages "en-US"
           :download.directory_upgrade true
           :safebrowsing.enabled false
           :safebrowsing.disable_download_protection true}})

(defn- get-driver-config [mode]
  (let [non-headless? (= "0"
                         (get (System/getenv) "HEADLESS"))
        dev? (= :development mode)]
    (assoc driver-defaults
           :download-dir (.getAbsolutePath (test-ctx :download-dir))
           :headless (cond
                       non-headless? false
                       dev? false
                       :else true))))

;; TODO these could use more of our wrapped fns if we reordered
(defn init-driver!
  "Starts and initializes a driver. Also stops an existing driver.

  `:browser-id` - You can specify e.g. `:chrome`.
  `:url`        - Specify a url of the server you want to test against or use default.
  `:mode`       - Specify `:development` if you wish to keep just one driver running, and not headless.

   Uses a non-headless browser if the environment variable HEADLESS is set to 0"
  [& [browser-id url mode]]
  (when (get-driver)
    (try
      (et/quit (get-driver))
      (catch Exception e)))
  (swap! (test-ctx)
         assoc-some
         :driver (et/with-wait-timeout 60
                   (-> browser-id
                       (et/boot-driver (get-driver-config mode))
                       (init-session!)))
         :url url
         :mode mode
         :seed (random-seed)))

(defn init-driver-fixture
  "Executes a test running a fresh driver except when in development."
  [f]
  (letfn [(run []
            (when-not (= :development (test-ctx :mode))
              (init-driver! :chrome))
            (f))]
    (try
      (run)
      (catch SocketException e
        (log/warn e "WebDriver failed to start, retrying...")
        (run)))))

(defn reset-context-fixture [f]
  (reset-context!)
  (f))

(defn smoke-test-fixture [f]
  (let [response (http/get (str (get-server-url) "js/app.js"))]
    (assert (= 200 (:status response))
            (str "Failed to load app.js: " response))
    (f)))

(defn ensure-empty-directories-fixture [f]
  (ensure-empty-directories!)
  (f))

(defn- get-sequence-number []
  (:sequence-number (swap! (test-ctx) update :sequence-number (fnil inc 0))))

;;; etaoin exported

(defn wait-for-idle
  "Use requestIdleCallback API to wait until browser has idle period. This may sometimes
   work more reliably than waiting for an arbitrary amount of time."
  ([]
   (wait-for-idle (get-driver) 200))

  ([timeout]
   (wait-for-idle (get-driver) timeout))

  ([driver timeout]
   (et/js-async driver
                (format
                 "var args = arguments;
                  var callback = args[args.length - 1];
                  window.requestIdleCallback(callback, { timeout: %d });" timeout))))

(defn- get-file-base
  "Get the base name for the util generated files."
  []
  (format "%03d-%s-"
          (get-sequence-number)
          (get-current-test-name)))

(defn ^:dynamic screenshot [filename]
  (let [driver (get-driver)
        _ (wait-for-idle driver 500)
        full-filename (str (get-file-base) filename ".png")
        file (io/file (test-ctx :reporting-dir) full-filename)

        window-size (et/get-window-rect driver)
        empty-space (if (et/exists? driver :empty-space) ; if page has not rendered, screenshot can fail due to missing element
                      (parse-int (et/get-element-attr driver :empty-space "clientHeight"))
                      0)

        without-extra-height (if (and empty-space
                                      (pos? empty-space))
                               (+ (- (:height window-size) empty-space) 100) ; with some margin
                               (:height window-size))
        need-to-adjust? (< 0 without-extra-height (:height window-size))]

    ;; adjust window to correct size
    (when need-to-adjust?
      (et/set-window-rect driver {:width (:width window-size)
                                  :height without-extra-height}))

    (et/screenshot driver file)

    ;; restore (big) size
    (when need-to-adjust?
      (et/set-window-rect driver window-size))))

(defn ^:dynamic screenshot-element [filename q]
  (let [full-filename (format "%03d-%s-%s"
                              (get-sequence-number)
                              (get-current-test-name)
                              filename)
        file (io/file (test-ctx :reporting-dir) full-filename)]
    (et/screenshot-element (get-driver)
                           q
                           file)))

(defn screenshot-ancestor
  "Like `screenshot-element` but take a screenshot of an ancestor to give context."
  [filename q]
  (screenshot-element filename [q {:xpath "./../../.."}]))

(defn dump-content [content filename]
  (when (seq content)
    (let [full-filename (format "%03d-%s-%s%s"
                                (get-sequence-number)
                                (get-current-test-name)
                                filename
                                ".txt")
          file (io/file (test-ctx :reporting-dir) full-filename)]
      (spit file (json/generate-string-pretty content)))))

(defn wait-predicate [pred & [explainer]]
  (try (et/wait-predicate pred) ; does not need driver
       (catch clojure.lang.ExceptionInfo ex
         (throw (ex-info "timed out in wait-predicate"
                         (merge (ex-data ex)
                                (when explainer
                                  {:explanation (explainer)}))
                         ex)))))

(defn running? []
  (et/running? (get-driver)))

(defn wrap-etaoin [f]
  (fn [& args] (apply f (get-driver) args)))

(def set-window-rect (wrap-etaoin et/set-window-rect))
(def go (wrap-etaoin et/go))
(def wait-visible (wrap-etaoin et/wait-visible))
(def wait-invisible (wrap-etaoin et/wait-invisible))
(def get-element-attr-el (wrap-etaoin et/get-element-attr-el))
(def get-element-attr (wrap-etaoin et/get-element-attr))
(def js-execute (wrap-etaoin et/js-execute))
(def js-async (wrap-etaoin et/js-async))
(def fill (wrap-etaoin et/fill))
(def fill-el (wrap-etaoin et/fill-el))
(def wait-has-class (wrap-etaoin et/wait-has-class))
(def get-element-text-el (wrap-etaoin et/get-element-text-el))
(def query (wrap-etaoin et/query))
(def query-all (wrap-etaoin et/query-all))
(def query-tree (wrap-etaoin et/query-tree))
(def child (wrap-etaoin et/child))
(def children (wrap-etaoin et/children))
(def upload-file (wrap-etaoin et/upload-file))
(def get-element-text (wrap-etaoin et/get-element-text))
(def click-el (wrap-etaoin et/click-el))
(def delete-cookies (wrap-etaoin et/delete-cookies))
(def get-url (wrap-etaoin et/get-url))
(def scroll-query (wrap-etaoin et/scroll-query))
(def click (wrap-etaoin et/click))
(def visible? (wrap-etaoin et/visible?))
(def displayed-el? (wrap-etaoin et/displayed-el?))
(def has-text? (wrap-etaoin et/has-text?))
(def has-class? (wrap-etaoin et/has-class?))
(def has-class-el? (wrap-etaoin et/has-class-el?))
(def disabled? (wrap-etaoin et/disabled?))
(def enabled? (wrap-etaoin et/enabled?))
(def wait-disabled (wrap-etaoin et/wait-disabled))
(def wait-enabled (wrap-etaoin et/wait-enabled))
(def clear (wrap-etaoin et/clear))
(def clear-el (wrap-etaoin et/clear-el))
(def wait-has-alert (wrap-etaoin et/wait-has-alert))
(def accept-alert (wrap-etaoin et/accept-alert))
(def reload (wrap-etaoin et/reload))
(def get-title (wrap-etaoin et/get-title))
(def exists? (wrap-etaoin et/exists?))
;; TODO add more of etaoin here

;;; etaoin extensions

;; exceptions make for ugly test failures: here are some wrappers that
;; are better adapted for (is ...) assertions

(defn no-timeout? [f]
  (try+
   (f)
   true
   (catch [:type :etaoin/timeout] e
     (log/error e)
     false)))

(defn eventually-exists? [& args]
  (wait-for-idle)
  (no-timeout? #(apply exists? args)))

(defn eventually-visible? [& args]
  (wait-for-idle)
  (no-timeout? #(apply wait-visible args)))

(defn eventually-invisible? [& args]
  (wait-for-idle)
  (no-timeout? #(apply wait-invisible args)))

(defn fill-human [q text]
  (wait-for-idle)
  (et/fill-human (get-driver) q text {:pause-max 0.03
                                      :mistake-prob 0.05}))

(defn visible-el?
  "Checks whether an element is visible on the page."
  [el]
  (displayed-el? el))

(defn wait-visible-el [el & [opt]]
  (let [message (format "Wait for %s element is visible" el)]
    (wait-for-idle)
    (wait-predicate #(visible-el? el)
                    (assoc opt :message message))))

(defn scroll-query-el
  "Scrolls to the element.

  Invokes element's `.scrollIntoView()` method. Accepts extra `param`
  argument that might be either boolean or object for more control.

  See this page for details:
  https://developer.mozilla.org/en-US/docs/Web/API/Element/scrollIntoView
  "
  ([el]
   (js-execute "arguments[0].scrollIntoView();" (et/el->ref el)))
  ([el param]
   (js-execute "arguments[0].scrollIntoView(arguments[1]);" (et/el->ref el) param)))

(defn scroll-and-click
  "Wait a button to become visible, scroll it to middle
  (to make sure it's not hidden under navigation) and click."
  [q & [opt]]
  (wait-for-idle)
  (wait-visible q opt)
  (scroll-query q {"block" "center"})
  (assert (not (get-element-attr q "disabled")))
  (click q))

(defn scroll-and-click-el
  "Wait a button to become visible, scroll it to middle
  (to make sure it's not hidden under navigation) and click."
  [el & [opt]]
  (wait-visible-el el opt)
  (scroll-query-el el {"block" "center"})
  (click-el el))

(defn wait-page-loaded []
  (wait-for-idle)
  (wait-invisible {:css ".fa-spinner"}))

(defn field-visible? [label]
  (or (visible? [{:css ".fields"}
                 {:tag :label :fn/has-text label}])
      (visible? [{:css ".fields"}
                 {:tag :legend :fn/has-text label}])))

(defn check-box [value]
  ;; XXX: assumes that the checkbox is unchecked
  (scroll-and-click [{:css (str "input[value='" value "']")}]))

(defn wait-for-downloads [string-or-regex]
  (wait-for-idle)
  (wait-predicate #(seq (downloaded-files string-or-regex))
                  #(do {:string-or-regex string-or-regex
                        :downloaded-files (downloaded-files)})))

(defn value-of-el
  "Return the \"value\" an element `el`.

  Value is the first of
  - aria-checked attribute of a read-only checkbox,
  - value attribute of an input or
  - element content text for others

  Mostly we check `el` children in case the structure is deep."
  [el]
  (->> [(try ; checkbox
          (when-let [checkbox-el (child el {:css ".checkbox"})]
            (= "true" (get-element-attr-el checkbox-el "aria-checked")))
          (catch Exception _))

        (try ; input with value
          (when-let [input-el (if (get-element-attr-el el "value")
                                el
                                (child el {:css "input"}))]
            (when-let [v (get-element-attr-el input-el "value")]
              (when-not (str/blank? v) ; e.g. dropdown doesn't use value
                (str/trim v))))
          (catch Exception _))

        ;; others
        (when-let [v (get-element-text-el el)]
          (str/trim v))]

       (remove nil?) ; we want false but not nil
       first))

(defn value-of
  "Like value-of-el, but with a selector"
  [selector]
  (value-of-el (query selector)))

(defn first-value-of-el
  "Return the first non-nil value of `el`.

  Optionally try the children of `el` with the given `selectors` to
  find the actual child element with the value.

  Value is defined by `value-of-el` function."
  [el & [selectors]]
  (->> (if (seq selectors)
         (mapcat #(children el %) selectors)
         [el])
       (remove nil?)
       (mapv value-of-el)
       first))

(def ^:private check-axe-js
  "var args = arguments;
   var callback = args[args.length-1]; // async callback
   window.axe.configure({ 'reporter': 'v2' });
   window.axe.run({ exclude: [['.dev-reload-button'],
                              ['body > div:not(#app)']]
   }).then(callback);")

(defn ^:dynamic check-axe
  "Runs automated accessibility tests using axe.

  Returns the test report.

  Ignores:
  - all divs of body except #app
  - our development tooling like .dev-reload-button

  See https://www.deque.com/axe/"
  []
  (when (:accessibility-report env)
    (js-async check-axe-js)))

(defn gather-axe-results
  "Runs automatic accessbility tests using `check-axe`
  and appends them to the test context for summary reporting.

  Produces a single screenshot `accessibility-violations` with
  all the violations marked with a border.

  The violations will also be dumped into a text file under the same `name`."
  [name]
  (let [results (check-axe)]
    (when-let [violations (seq (:violations results))]
      (let [targets (select [ALL :nodes ALL :target ALL] violations)
            originals (doall (for [target targets]
                               [target (js-execute (str "var x = document.querySelector('" target "'); return x && x.style && x.style.outline; "))]))]
        (doseq [target targets]
          (js-execute (str "var x = document.querySelector('" target "'); if (x && x.style) x.style.outline = \"3px dashed red\";")))

        (screenshot (str name "-accessibility-violations"))
        (dump-content violations (str name "-accessibility-violations"))

        ;; let's restore the results just in case further
        ;; tests would be affected
        (doseq [[target original] originals]
          (when original
            (js-execute (str "var x = document.querySelector('" target "'); if (x && x.style) x.style.outline = \"" original "\";"))))))
    (swap! (test-ctx) update :axe conj-vec results)))

(defn accessibility-report-fixture
  "Runs the tests and finally stores the gathered accessibility
  results into summary files.

  NB: the individual tests must call `gather-axe-results` in each
  interesting spot to have a sensible report to write."
  [f]
  (swap! (test-ctx) dissoc :axe)
  (try
    (f)
    (finally
      (let [violations (atom nil)]
        (doseq [k (->> (test-ctx :axe)
                       (mapcat keys)
                       distinct)
                :let [filename (str (str/lower-case (name k)) ".json")
                      content (->> (test-ctx :axe)
                                   (mapcat #(get % k))
                                   distinct
                                   (sort-by :impact))]]
          (spit (io/file (test-ctx :accessibility-report-dir) filename)
                (json/generate-string-pretty content))
          (when (and (= :violations k) (seq content))
            (reset! violations content)))
        (when (seq @violations)
          (throw (Exception. (str "\n\n\nThere are accessibility violations: " (count @violations) "\n\n\n"))))))))

(defmacro with-client-config [config & body]
  `(do
     (rems.browser-test-util/js-execute
      "return window.rems.app.mergeConfig(arguments[0]);" ~config)
     ~@body
     (rems.browser-test-util/js-async
      "var args = arguments;
       var callback = args[args.length - 1];
       return window.rems.app.fetchConfig(callback);")))

(defn autosave-enabled? []
  (get env :enable-autosave false))

(defn ^:dynamic postmortem-handler
  "Simplified version of `etaoin.api/postmortem-handler`"
  [ex]
  (let [driver (get-driver)
        dir (test-ctx :reporting-dir)]

    (io/make-parents dir)

    (screenshot (str "error-screenshot"))

    (spit (io/file dir (str (get-file-base) "error-stacktrace.txt"))
          (with-out-str (clojure.stacktrace/print-stack-trace ex)))

    (spit (io/file dir (str (get-file-base) "error-page.html"))
          (et/get-source driver))

    (when (et/supports-logs? driver)
      (#'et/dump-logs (et/get-logs driver)
                      (io/file dir (str (get-file-base) "error-logs.json"))))))

(defmacro with-postmortem
  "A custom `etaoin.api/with-postmortem` that saves
  everything in sequentially named files."
  [& body]
  `(try
     ~@body
     (catch Exception e#
       (rems.browser-test-util/postmortem-handler e#)
       (throw e#))))
