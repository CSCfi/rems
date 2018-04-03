(ns rems.auth.auth
  (:require [re-frame.core :as rf]
            [rems.auth.ldap :as ldap]
            [rems.auth.shibboleth :as shibboleth])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn login-component []
  (let [config @(rf/subscribe [:rems.config/config])]
    (case (:authentication config)
      :shibboleth (shibboleth/login-component)
      :fake-shibboleth (shibboleth/login-component)
      :ldap (ldap/login-component)
      nil)))

(defn guide []
  [:div
   (example "shibboleth login" [shibboleth/login-component])
   (example "ldap login" [ldap/login-component])])
