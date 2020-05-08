(ns ^:browser rems.browser-test-util
  "Browser test utils.

  NB: Don't use etaoin directly but wrap it to functions that don't need the driver to be passed."
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [etaoin.api :as et]
            [medley.core :refer [assoc-some]]
            [rems.api.testing :refer [standalone-fixture]]
            [rems.config]
            [rems.standalone]
            [rems.browser-test-util :as btu])
  (:import (java.net SocketException)))

;;; test setup

(defonce test-context
  (atom {:url "http://localhost:3001/"
         :mode :test
         :seed "circle"}))

(defn get-driver [] (:driver @test-context))
(defn get-server-url [] (:url @test-context))
(defn get-seed [] (:seed @test-context))
(defn context-get [k] (get @test-context k))
(defn context-assoc! [& args] (swap! test-context #(apply assoc % args)))

(defn- delete-files [dir]
  (doseq [file (.listFiles dir)]
    (io/delete-file file true)))

(def reporting-dir
  (doto (io/file "browsertest-errors")
    (.mkdirs)
    (delete-files)))

(defn- mod-nth [coll i]
  (nth coll (mod (int i) (count coll))))

(defn- random-seed []
  (let [t (System/currentTimeMillis)]
    (str (mod-nth ["red" "green" "blue" "magenta" "cyan" "yellow" "white" "black" "brown" "pink"] (/ t 60000))
         " "
         (mod-nth ["amusing" "comic" "funny" "laughable" "hilarious" "witty" "jolly" "silly" "ludicrous" "wacky"] (/ t 1000))
         " "
         (mod-nth ["leopard" "gorilla" "turtle" "orangutan" "elephant" "saola" "vaquita" "tiger" "rhino" "pangolin"] (mod t 123)))))

;; TODO these could use more of our wrapped fns if we reordered
(defn init-driver!
  "Starts and initializes a driver. Also stops an existing driver.

  `:browser-id` - You can specify e.g. `:chrome`.
  `:url`        - Specify a url of the server you want to test against or use default.
  `:mode`       - Specify `:development` if you wish to keep just one driver running, and not headless."
  [& [browser-id url mode]]
  (when (get-driver) (try (et/quit (get-driver)) (catch Exception e)))
  (swap! test-context
         assoc-some
         :driver (et/boot-driver browser-id
                                 {:args ["--lang=en-US"]
                                  :prefs {"intl.accept_languages" "en-US"}
                                  :headless (not= :development mode)})
         :url url
         :mode mode
         :seed (random-seed))
  (et/delete-cookies (get-driver))) ; start with a clean slate

(defn fixture-driver
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

(defn smoke-test [f]
  (let [response (http/get (str (get-server-url) "js/app.js"))]
    (assert (= 200 (:status response))
            (str "Failed to load app.js: " response))
    (f)))

(def test-dev-or-standalone-fixture
  (join-fixtures [(fn [f]
                    (if (= :development (:mode @test-context))
                      (f)
                      (standalone-fixture f)))
                  smoke-test]))



;;; etaoin exported

(defn wrap-etaoin [f]
  (fn [& args] (apply f (get-driver) args)))

(def set-window-size (wrap-etaoin et/set-window-size))
(def go (wrap-etaoin et/go))
(def screenshot (wrap-etaoin et/screenshot))
(def wait-visible (wrap-etaoin et/wait-visible))
(def wait-invisible (wrap-etaoin et/wait-invisible))
(def query-all (wrap-etaoin et/query-all))
(def get-element-attr-el (wrap-etaoin et/get-element-attr-el))
(def fill-human (wrap-etaoin et/fill-human))
(def get-element-attr (wrap-etaoin et/get-element-attr))
(def js-execute (wrap-etaoin et/js-execute))
(def fill (wrap-etaoin et/fill))
(def wait-has-class (wrap-etaoin et/wait-has-class))
(def get-element-text-el (wrap-etaoin et/get-element-text-el))
(def query (wrap-etaoin et/query))
(def child (wrap-etaoin et/child))
(def upload-file (wrap-etaoin et/upload-file))
(def get-element-text (wrap-etaoin et/get-element-text))
(def click-el (wrap-etaoin et/click-el))
(def delete-cookies (wrap-etaoin et/delete-cookies))
(def get-url (wrap-etaoin et/get-url))
(def scroll-query (wrap-etaoin et/scroll-query))
(def click (wrap-etaoin et/click))
(def visible? (wrap-etaoin et/visible?))
(defmacro with-postmortem [& args] `(et/with-postmortem (get-driver) ~@args))
(def wait-predicate et/wait-predicate) ; does not need driver
;; TODO add more of etaoin here



;;; etaoin extensions

(defn scroll-and-click
  "Wait a button to become visible, scroll it to middle
  (to make sure it's not hidden under navigation) and click."
  [q & [opt]]
  (wait-visible q opt)
  (scroll-query q {"block" "center"})
  (click q))

(defn wait-page-loaded []
  (wait-invisible {:css ".fa-spinner"}))


(defn field-visible? [label]
  (visible? [{:css ".fields"}
             {:tag :label :fn/has-text label}]))

(defn check-box [value]
  ;; XXX: assumes that the checkbox is unchecked
  (scroll-and-click [{:css (str "input[value='" value "']")}]))
