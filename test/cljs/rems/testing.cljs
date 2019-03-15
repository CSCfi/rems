(ns rems.testing
  (:require [re-frame.core :as rf]))

(defn isolate-re-frame-state [f]
  (let [restore-fn (rf/make-restore-fn)]
    (try
      (f)
      (finally
        (restore-fn)))))

(defn stub-re-frame-effect [id]
  (rf/clear-fx id)
  (rf/reg-fx id (fn [_])))
