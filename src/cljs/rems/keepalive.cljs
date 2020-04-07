(ns rems.keepalive
  (:require [cljs-time.core :as time]
            [re-frame.core :as rf]
            [rems.util :refer [fetch]]))

(def keepalive-interval (time/minutes 1))

(defn keepalive! []
  (fetch "/api/keepalive" {}))

(rf/reg-event-db
 ::activity
 (fn [db _ ]
   (let [now (time/now)
         deadline (get db ::next-keepalive (time/date-time 1970))]
     (if (time/before? now deadline)
       db
       (do
         (keepalive!)
         (assoc db ::next-keepalive (time/plus now keepalive-interval)))))))

(defn register-keepalive-listeners! []
  (doseq [event ["mousedown", "mousemove", "keydown", "scroll", "touchstart"]]
    (.addEventListener js/document event (fn [_] (rf/dispatch [::activity])) true)))
