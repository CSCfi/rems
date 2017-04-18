(ns rems.browser
  (:require [clj-webdriver.taxi :refer :all]
            [mount.core :as mount]))

(def ^:private browser-count (atom 0))

(defn browser-up
  "Start up a browser if it's not already started."
  []
  (when (= 1 (swap! browser-count inc))
    (set-driver! {:browser :firefox})
    (implicit-wait 60000)))

(defn browser-down
  "If this is the last request, shut the browser down."
  [& {:keys [force] :or {force false}}]
  (when (zero? (swap! browser-count (if force (constantly 0) dec)))
    (quit)))

(defn with-browser [f]
  (browser-up)
  (f)
  (browser-down))

(defn with-server [f]
  (mount/start)
  (f)
  (mount/stop))
