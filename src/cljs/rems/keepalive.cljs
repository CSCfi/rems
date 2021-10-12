(ns rems.keepalive
  (:require [cljs-time.core :as time]
            [re-frame.core :as rf]
            [rems.util :refer [fetch]]
            [goog.functions :refer [throttle]]))

(def keepalive-interval (time/minutes 1))

(defn keepalive! []
  (fetch "/api/keepalive" {:response-format nil})) ;; the response is empty

(rf/reg-event-db
 ::activity
 (fn [db _]
   (let [now (time/now)
         deadline (get db ::next-keepalive (time/date-time 1970))]
     (if (time/before? now deadline)
       db
       (do
         (keepalive!)
         (assoc db ::next-keepalive (time/plus now keepalive-interval)))))))

(defn- keepalive-listener [_]
  (rf/dispatch [::activity]))

(defn register-keepalive-listeners! []
  (let [throttled-keepalive-listener (throttle keepalive-listener 10000)]
    (doseq [event ["mousedown", "mousemove", "keydown", "scroll", "touchstart"]]
      (.addEventListener js/document event throttled-keepalive-listener true))))
