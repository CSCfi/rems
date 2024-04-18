(ns rems.theme
  (:require [rems.config]
            [rems.globals]))

(defn use-navbar-logo? []
  (let [theme @rems.globals/theme
        lang (some-> @rems.config/language-or-default name)
        navbar-logo (or (get theme (keyword (str "navbar-logo-name-" lang)))
                        (get theme :navbar-logo-name))]
    (some? navbar-logo)))
