(ns rems.auth.auth
  (:require [re-frame.core :as rf]
            [rems.auth.ldap :as ldap]
            [rems.auth.oidc :as oidc]
            [rems.auth.shibboleth :as shibboleth])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn login-component []
  (let [config @(rf/subscribe [:rems.config/config])
        alternative-endpoint (:alternative-login-url config)
        login-component (case (:authentication config)
                          :shibboleth (shibboleth/login-component alternative-endpoint)
                          :fake-shibboleth (shibboleth/login-component alternative-endpoint)
                          :ldap (ldap/login-component)
                          :oidc (oidc/login-component)
                          nil)]
    (when login-component
      [:div.jumbotron.login-component
       login-component])))

(defn guide []
  [:div
   (component-info shibboleth/login-component)
   (example "shibboleth login" [shibboleth/login-component nil])
   (example "shibboleth login with alternatives" [shibboleth/login-component "/alternative"])
   (component-info ldap/login-component)
   (example "ldap login" [ldap/login-component])
   (component-info oidc/login-component)
   (example "oidc login" [oidc/login-component])])
