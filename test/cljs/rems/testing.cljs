(ns rems.testing
  (:require [re-frame.core :as rf]
            [rems.globals]))

(defn- clear-rf-state! []
  #_(rf/clear-cofx)
  #_(rf/clear-event)
  #_(rf/clear-global-interceptor)
  #_(rf/clear-sub)
  (rf/clear-subscription-cache!))

(defn- reset-globals! []
  (reset! rems.globals/config {:default-language :en
                               :languages [:en]}))

(defn init-spa-fixture [f]
  (let [restore-fn (rf/make-restore-fn)]
    (try
      (clear-rf-state!)
      (reset-globals!)
      (f)
      (finally
        (restore-fn)))))

(defn stub-re-frame-effect [id]
  (rf/clear-fx id)
  (rf/reg-fx id (fn [_])))
