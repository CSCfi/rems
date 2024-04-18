(ns rems.theme
  (:require [re-frame.core :as rf]
            [rems.config]))

(rf/reg-sub :rems.theme/theme (fn [db] (::theme db)))
(rf/reg-event-db :rems.theme/loaded-theme (fn [db [_ theme]] (assoc db ::theme theme)))

(defn use-navbar-logo? []
  (let [theme @(rf/subscribe [:rems.theme/theme])
        lang (some-> @rems.config/language-or-default name)
        navbar-logo (or (get theme (keyword (str "navbar-logo-name-" lang)))
                        (get theme :navbar-logo-name))]
    (some? navbar-logo)))
