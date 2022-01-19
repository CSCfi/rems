(ns rems.keepalive
  (:require [rems.util :refer [fetch]]
            [goog.functions :refer [throttle]]))

(defn keepalive! []
  (fetch "/api/keepalive" {:response-format nil})) ;; the response is empty

(defn register-keepalive-listeners! []
  (let [throttled-keepalive-listener (throttle keepalive! 60000)]
    (doseq [event ["mousedown", "mousemove", "keydown", "scroll", "touchstart"]]
      (.addEventListener js/document event throttled-keepalive-listener (js-obj "passive" true)))))
