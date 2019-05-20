(ns rems.auth.auth
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.auth.ldap :as ldap]
            [rems.auth.shibboleth :as shibboleth]
            [rems.navbar :as navbar]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn login-component []
  (let [config @(rf/subscribe [:rems.config/config])
        alternative-endpoint (:alternative-login-url config)
        alternative-embed (:alternative-login-embed config)]
    [:div.jumbotron
     (case (:authentication config)
       :shibboleth (shibboleth/login-component)
       :fake-shibboleth (shibboleth/login-component)
       :ldap (ldap/login-component)
       nil)
     (when alternative-embed
       [:iframe {:src alternative-embed}])
     (when alternative-endpoint
       [atoms/link-to nil
        (navbar/url-dest alternative-endpoint)
        (text :t.login/alternative)])]))

(defn guide []
  [:div
   (example "shibboleth login" [:div.jumbotron [shibboleth/login-component]])
   (example "ldap login" [:div.jumbotron [ldap/login-component]])])
