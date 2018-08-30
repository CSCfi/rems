(ns rems.test.testing
  (:require [re-frame.core :as rf]))

(defn isolate-re-frame-state [f]
  (let [restore-fn (rf/make-restore-fn)]
    (try
      (f)
      (finally
        (restore-fn)))))
