(ns ^:browser rems.browser-test-util
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

(def set-window-size et/set-window-size)
(def go et/go)
(def screenshot et/screenshot)
(def wait-visible et/wait-visible)
(def query-all et/query-all)
(def get-element-attr-el et/get-element-attr-el)
(def fill-human et/fill-human)
(def get-element-attr et/get-element-attr)
(def js-execute et/js-execute)
(def fill et/fill)
(def wait-has-class et/wait-has-class)
(def get-element-text-el et/get-element-text-el)
(def query et/query)
(def child et/child)
(defmacro with-postmortem [& args] `(et/with-postmortem ~@args))
(def upload-file et/upload-file)
(def get-element-text et/get-element-text)
(def click-el et/click-el)
(def delete-cookies et/delete-cookies)
(def get-url et/get-url)

;;; etaoin extensions

(defn wait-predicate
  "The etaoin API is not very consistent, it does not want driver here, so let's wrap it!"
  [_driver pred]
  (et/wait-predicate pred))

(defn scroll-and-click
  "Wait a button to become visible, scroll it to middle
  (to make sure it's not hidden under navigation) and click."
  [driver q & [opt]]
  (doto driver
    (et/wait-visible q opt)
    (et/scroll-query q {"block" "center"})
    (et/click q)))

(defn wait-page-loaded [driver]
  (et/wait-invisible driver {:css ".fa-spinner"}))


(defn field-visible? [driver label]
  (et/visible? driver [{:css ".fields"}
                       {:tag :label :fn/has-text label}]))

(defn check-box [driver value]
  ;; XXX: assumes that the checkbox is unchecked
  (scroll-and-click driver [{:css (str "input[value='" value "']")}]))
