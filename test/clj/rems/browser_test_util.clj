(ns rems.browser-test-util
  "Browser test utils.

  NB: Don't use etaoin directly but wrap it to functions that don't need the driver to be passed."
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.stacktrace]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [com.rpl.specter :refer [ALL select]]
            [etaoin.api :as et]
            [medley.core :refer [assoc-some]]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.common.util :refer [conj-vec getx parse-int]]
            [rems.config :refer [env]]
            [rems.db.api-key :as api-key]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.test-data-users :as test-users]
            [rems.service.test-data :as test-data]
            [rems.json :as json]
            [rems.main]
            [rems.util :refer [ensure-empty-directory!]]
            [slingshot.slingshot :refer [try+]])
  (:import [java.net SocketException]))

;;; test setup

(defonce test-context
  (atom {:url "http://localhost:3001/"
         :mode :test
         :seed "circle"
         :reporting-dir (io/file "browsertest-errors")
         :accessibility-report-dir (io/file "browsertest-accessibility-report")
         :download-dir (io/file "browsertest-downloads")}))

(defn get-driver [] (:driver @test-context))
(defn get-server-url [] (:url @test-context))
(defn get-seed [] (:seed @test-context))
(defn context-get [k] (get @test-context k))
(defn context-getx [k] (getx @test-context k))
(defn context-assoc! [& args] (swap! test-context #(apply assoc % args)))
(defn context-dissoc! [& args] (swap! test-context #(apply dissoc % args)))
(defn context-update! [& args] (swap! test-context #(apply update % args)))

(defn- ensure-empty-directories! []
  (ensure-empty-directory! (:reporting-dir @test-context))
  (ensure-empty-directory! (:accessibility-report-dir @test-context))
  (ensure-empty-directory! (:download-dir @test-context)))

(defn downloaded-files [name-or-regex]
  (if (string? name-or-regex)
    (let [f (io/file (:download-dir @test-context) name-or-regex)]
      (when (.exists f) [f]))
    (for [file (.listFiles (:download-dir @test-context))
          :when (re-matches name-or-regex (.getName file))]
      file)))

(defn delete-downloaded-files! [name-or-regex]
  (let [files (if (string? name-or-regex)
                [(io/file (:download-dir @test-context) name-or-regex)]
                (for [file (.listFiles (:download-dir @test-context))
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
                               :downloadPath (.getAbsolutePath (:download-dir @test-context))}}}))

;; TODO these could use more of our wrapped fns if we reordered
(defn init-driver!
  "Starts and initializes a driver. Also stops an existing driver.

  `:browser-id` - You can specify e.g. `:chrome`.
  `:url`        - Specify a url of the server you want to test against or use default.
  `:mode`       - Specify `:development` if you wish to keep just one driver running, and not headless.

   Uses a non-headless browser if the environment variable HEADLESS is set to 0"
  [& [browser-id url mode]]
  (when (get-driver) (try (et/quit (get-driver)) (catch Exception e)))
  (swap! test-context
         assoc-some
         :driver (et/with-wait-timeout 60
                   (et/boot-driver browser-id
                                   {:args ["--lang=en-US"]
                                    :prefs {:intl.accept_languages "en-US"
                                            :download.directory_upgrade true
                                            :safebrowsing.enabled false
                                            :safebrowsing.disable_download_protection true}
                                    :download-dir (.getAbsolutePath (:download-dir @test-context))
                                    :headless (not (or (= "0" (get (System/getenv) "HEADLESS"))
                                                       (= :development mode)))}))
         :url url
         :mode mode
         :seed (random-seed))
  (enable-downloads! (get-driver)))

(defn refresh-driver!
  "Refreshes an existing driver, cleans up and sets default values."
  []
  (assert (get-driver) "must have initialized driver already!")
  ;; start with a clean slate
  (et/delete-cookies (get-driver))
  ;; big enough to show the whole page in the screenshots
  (et/set-window-size (get-driver) 1400 7000))

(defn fixture-init-driver
  "Executes a test running a fresh driver except when in development."
  [f]
  (letfn [(run []
            (when-not (= :development (:mode @test-context))
              (init-driver! :chrome))
            (f))]
    (try
      (run)
      (catch SocketException e
        (log/warn e "WebDriver failed to start, retrying...")
        (run)))))

(defn fixture-refresh-driver
  "Executes a test running with a re-used but clean and refreshed driver."
  [f]
  (try
    (refresh-driver!)
    (f)
    (catch clojure.lang.ExceptionInfo e
      ;; could need a restart
      (let [data (ex-data e)]
        (if (= "invalid session id" (get-in data [:response :value :error]))
          (do
            (log/warn e "Unexpected problem, need to restart driver" data)
            (fixture-init-driver f))
          (throw e))))))

(defn smoke-test [f]
  (let [response (http/get (str (get-server-url) "js/app.js"))]
    (assert (= 200 (:status response))
            (str "Failed to load app.js: " response))
    (f)))

(defn- create-test-data [f]
  (test-helpers/assert-no-existing-data!)
  (api-key/add-api-key! 42 {:comment "test data"})
  ;; Organizations
  (test-helpers/create-organization! {:actor "owner"})
  (test-helpers/create-organization! {:actor "owner"
                                      :organization/id "nbn"
                                      :organization/name {:fi "NBN" :en "NBN" :sv "NBN"}
                                      :organization/short-name {:fi "NBN" :en "NBN" :sv "NBN"}
                                      :organization/owners [{:userid "organization-owner2"}]
                                      :organization/review-emails []})
  ;; Users
  (test-helpers/create-user! (get test-users/+fake-user-data+ "owner"))
  (test-helpers/create-user! (get test-users/+fake-user-data+ "carl"))
  (test-helpers/create-user! (get test-users/+fake-user-data+ "handler"))
  (test-helpers/create-user! (get test-users/+fake-user-data+ "reporter") :reporter)
  (test-helpers/create-user! {:userid "applicant" :organizations [{:organization/id "default"}]})
  (test-helpers/create-user! (get test-users/+fake-user-data+ "alice"))
  (test-helpers/create-user! (get test-users/+fake-user-data+ "developer"))
  (test-helpers/create-workflow! nil) ;;master workflow
  ;; Forms, workflows etc.
  ;; These should match the rems.service.test-data closely enough
  ;; so that one can use also development mode and dev db
  ;; with the tests
  (let [link (test-helpers/create-license! {:actor "owner"
                                            :license/type :link
                                            :organization {:organization/id "nbn"}
                                            :license/title {:en "CC Attribution 4.0"
                                                            :fi "CC Nimeä 4.0"
                                                            :sv "CC Erkännande 4.0"}
                                            :license/link {:en "https://creativecommons.org/licenses/by/4.0/legalcode"
                                                           :fi "https://creativecommons.org/licenses/by/4.0/legalcode.fi"
                                                           :sv "https://creativecommons.org/licenses/by/4.0/legalcode.sv"}})
        text (test-helpers/create-license! {:actor "owner"
                                            :license/type :text
                                            :organization {:organization/id "nbn"}
                                            :license/title {:en "General Terms of Use"
                                                            :fi "Yleiset käyttöehdot"
                                                            :sv "Allmänna villkor"}
                                            :license/text {:en (apply str (repeat 10 "License text in English. "))
                                                           :fi (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))
                                                           :sv (apply str (repeat 10 "Licens på svenska. "))}})
        wfid (test-helpers/create-workflow! {:type :workflow/default
                                             :title "Default workflow"
                                             :handlers ["handler" "developer"]
                                             :licenses [link text]})
        decider-wf (test-helpers/create-workflow! {:actor "owner"
                                                   :organization {:organization/id "nbn"}
                                                   :title "Decider workflow"
                                                   :type :workflow/decider
                                                   :handlers ["carl" "handler"]
                                                   :licenses [link text]})
        form (test-data/create-all-field-types-example-form! "owner" {:organization/id "nbn"} "Example form with all field types" {:en "Example form with all field types"
                                                                                                                                   :fi "Esimerkkilomake kaikin kenttätyypein"
                                                                                                                                   :sv "Exempelblankett med alla fälttyper"})
        _simple-form (test-helpers/create-form! {:actor "owner"
                                                 :organization {:organization/id "nbn"}
                                                 :form/internal-name "Simple form"
                                                 :form/external-title {:en "Simple Form"
                                                                       :fi "Yksinkertainen lomake"
                                                                       :sv "Enkelt Blankett"}
                                                 :form/fields [{:field/title {:en "Simple text field"
                                                                              :fi "Yksinkertainen tekstikenttä"
                                                                              :sv "Textfält"}
                                                                :field/optional false
                                                                :field/type :text
                                                                :field/max-length 100
                                                                :field/privacy :private}]})
        res-id1 (test-helpers/create-resource! nil)
        res-id2 (test-helpers/create-resource! nil)
        ;; duo-resource (test-helpers/create-resource! {:resource-ext-id "All DUO codes with restrictions"
        ;;                                              :organization {:organization/id "nbn"}
        ;;                                              :resource/duo {:duo/codes [{:id "DUO:0000007" :restrictions [{:type :mondo
        ;;                                                                                                            :values ["MONDO:0000015"]}]}
        ;;                                                                         {:id "DUO:0000012" :restrictions [{:type :topic
        ;;                                                                                                            :values ["my research type"]}]}
        ;;                                                                         {:id "DUO:0000020" :restrictions [{:type :collaboration
        ;;                                                                                                            :values ["developers"]}]}
        ;;                                                                         {:id "DUO:0000022" :restrictions [{:type :location
        ;;                                                                                                            :values ["egentliga finland"]}]}
        ;;                                                                         {:id "DUO:0000024" :restrictions [{:type :date
        ;;                                                                                                            :values ["2021-10-29"]}]}
        ;;                                                                         {:id "DUO:0000025" :restrictions [{:type :months
        ;;                                                                                                            :values ["120"]}]}
        ;;                                                                         {:id "DUO:0000026" :restrictions [{:type :users
        ;;                                                                                                            :values ["alice"]}]}
        ;;                                                                         {:id "DUO:0000027" :restrictions [{:type :project
        ;;                                                                                                            :values ["rems"]}]}
        ;;                                                                         {:id "DUO:0000028" :restrictions [{:type :institute
        ;;                                                                                                            :values ["csc"]}]}]}})
        item-id1 (test-helpers/create-catalogue-item! {:form-id form :workflow-id wfid :title {:en "Default workflow" :fi "Oletustyövuo"
                                                                                               :sv "Standard arbetsflöde"} :resource-id res-id1})
        _ (test-helpers/create-catalogue-item! {:form-id _simple-form
                                                :workflow-id wfid
                                                :title {:en "Default workflow with private form"
                                                        :fi "Oletustyövuo yksityisellä lomakkeella"
                                                        :sv "Standard arbetsflöde med privat blankett"}
                                                :resource-id res-id2})
        ;; item-id3 (test-helpers/create-catalogue-item! {:form-id _simple-form :workflow-id wfid :title {:en "Default workflow with DUO codes" :fi "Oletustyövuo DUO-koodeilla"
        ;;                                                                                                :sv "Standard blankettarbetsflöde med DUO-koder"} :resource-id duo-resource})
        app-id (test-helpers/create-draft! "applicant" [item-id1] "draft")]
    (test-helpers/submit-application {:application-id app-id
                                      :actor "applicant"}))
  (f))
(defn test-dev-or-standalone-fixture
  "Depending on if we are trying to develop browser tests or
  run them for real, we use an existing server and db or
  boot everything up and recreate test data too."
  [f]
  (if (= :development (:mode @test-context))
    (f)
    ((compose-fixtures standalone-fixture create-test-data) f)))

(defn ensure-empty-directories-fixture [f]
  (ensure-empty-directories!)
  (f))

(defn- get-sequence-number []
  (:sequence-number (swap! test-context update :sequence-number (fnil inc 0))))

(defn- get-current-test-name []
  (if-let [test-var (first clojure.test/*testing-vars*)]
    (name (symbol test-var))
    "unknown"))

;;; etaoin exported

(defn- get-file-base
  "Get the base name for the util generated files."
  []
  (format "%03d-%s-"
          (get-sequence-number)
          (get-current-test-name)))

(defn screenshot [filename]
  (let [driver (get-driver)
        full-filename (str (get-file-base) filename ".png")
        file (io/file (:reporting-dir @test-context) full-filename)
        window-size (et/get-window-size driver)
        empty-space (parse-int (et/get-element-attr driver :empty-space "clientHeight"))

        without-extra-height (if (and empty-space
                                      (pos? empty-space))
                               (+ (- (:height window-size) empty-space) 100) ; with some margin
                               (:height window-size))
        need-to-adjust? (< 0 without-extra-height (:height window-size))]

    ;; adjust window to correct size
    (when need-to-adjust?
      (et/set-window-size driver {:width (:width window-size)
                                  :height without-extra-height}))

    (et/screenshot driver file)

    ;; restore (big) size
    (when need-to-adjust?
      (et/set-window-size driver window-size))))

(defn screenshot-element [filename q]
  (let [full-filename (format "%03d-%s-%s"
                              (get-sequence-number)
                              (get-current-test-name)
                              filename)
        file (io/file (:reporting-dir @test-context) full-filename)]
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
          file (io/file (:reporting-dir @test-context) full-filename)]
      (spit file (json/generate-string-pretty content)))))

(defn wait-predicate [pred & [explainer]]
  (try (et/wait-predicate pred) ; does not need driver
       (catch clojure.lang.ExceptionInfo ex
         (throw (ex-info "timed out in wait-predicate"
                         (merge (ex-data ex)
                                (when explainer
                                  {:explanation (explainer)}))
                         ex)))))

(defn wrap-etaoin [f]
  (fn [& args] (apply f (get-driver) args)))

(def set-window-size (wrap-etaoin et/set-window-size))
(def go (wrap-etaoin et/go))
(def wait-visible (wrap-etaoin et/wait-visible))
(def wait-invisible (wrap-etaoin et/wait-invisible))
(def query-all (wrap-etaoin et/query-all))
(def get-element-attr-el (wrap-etaoin et/get-element-attr-el))
(def get-element-attr (wrap-etaoin et/get-element-attr))
(def js-execute (wrap-etaoin et/js-execute))
(def js-async (wrap-etaoin et/js-async))
(def fill (wrap-etaoin et/fill))
(def wait-has-class (wrap-etaoin et/wait-has-class))
(def get-element-text-el (wrap-etaoin et/get-element-text-el))
(def query (wrap-etaoin et/query))
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
(def clear (wrap-etaoin et/clear))
(def clear-el (wrap-etaoin et/clear-el))
(def wait-has-alert (wrap-etaoin et/wait-has-alert))
(def accept-alert (wrap-etaoin et/accept-alert))
(def reload (wrap-etaoin et/reload))
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

(defn eventually-visible? [& args]
  (no-timeout? #(apply wait-visible args)))

(defn eventually-invisible? [& args]
  (no-timeout? #(apply wait-invisible args)))

;; TODO our input fields process every character through re-frame.
;; Etaoin's fill-human almost works, but very rarely loses characters,
;; probably due to the lack of a _minimum_ delay between keypresses.
;; This is a reimplementation.
(def +character-delay+ 0.01)
(def +max-extra-delay+ 0.2)
(def +typo-probability+ 0.05)

(defn fill-human [q text]
  (wait-visible q)
  (doseq [c text]
    (et/wait (* +max-extra-delay+ (Math/pow (rand) 5)))
    (when (< (rand) +typo-probability+)
      (et/wait +character-delay+)
      (et/fill (get-driver) q (char (inc (int c))))
      (et/wait +character-delay+)
      (et/fill (get-driver) q \backspace))
    (et/wait +character-delay+)
    (et/fill (get-driver) q c))
  (et/wait +character-delay+)
  (let [value (get-element-attr q "value")]
    (when-not (= text value)
      (log/warn "Failed to fill field to" (pr-str text) "got" (pr-str value) "instead."))))

(defn visible-el?
  "Checks whether an element is visible on the page."
  [el]
  (displayed-el? el))

(defn wait-visible-el [el & [opt]]
  (let [message (format "Wait for %s element is visible" el)]
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
  (wait-predicate #(seq (downloaded-files string-or-regex))))

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

(defn check-axe
  "Runs automated accessibility tests using axe.

  Returns the test report.

  Ignores:
  - all divs of body except #app
  - our development tooling like .dev-reload-button

  See https://www.deque.com/axe/"
  []
  (let [result (js-async "
    var args = arguments;
    var callback = args[args.length-1];
    window.axe.configure({'reporter': 'v2'});
    window.axe.run({ exclude:[['.dev-reload-button'],
                              ['body > div:not(#app)']]})

    .then(callback);")]
    result))

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
    (context-update! :axe conj-vec results)))

(defn accessibility-report-fixture
  "Runs the tests and finally stores the gathered accessibility
  results into summary files.

  NB: the individual tests must call `gather-axe-results` in each
  interesting spot to have a sensible report to write."
  [f]
  (context-dissoc! :axe)
  (try
    (f)
    (finally
      (let [violations (atom nil)]
        (doseq [k (->> (context-get :axe)
                       (mapcat keys)
                       distinct)
                :let [filename (str (str/lower-case (name k)) ".json")
                      content (->> (context-get :axe)
                                   (mapcat #(get % k))
                                   distinct
                                   (sort-by :impact))]]
          (spit (io/file (:accessibility-report-dir @test-context) filename)
                (json/generate-string-pretty content))
          (when (and (= :violations k) (seq content))
            (reset! violations content)))
        (when (seq @violations)
          (throw (Exception. (str "\n\n\nThere are accessibility violations: " (count @violations) "\n\n\n"))))))))

(defmacro with-client-config [config & body]
  `(do
     (rems.browser-test-util/js-execute
      "return window.rems.config.set_config_BANG_(arguments[0]);" ~config)
     ~@body
     (rems.browser-test-util/js-async
      "var args = arguments;
       var callback = args[args.length - 1];
       return window.rems.config.fetch_config_BANG_(callback);")))

(defn autosave-enabled? []
  (get env :enable-autosave false))

(defn postmortem-handler
  "Simplified version of `etaoin.api/postmortem-handler`"
  [ex]
  (let [driver (get-driver)
        dir (:reporting-dir @test-context)]

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

