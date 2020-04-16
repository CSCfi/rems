(ns rems.auth.auth
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.auth.oidc :as oidc]
            [rems.auth.shibboleth :as shibboleth]
            [rems.navbar :as nav]
            [rems.text :refer [text]])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

(defn login-component []
  (let [config @(rf/subscribe [:rems.config/config])
        login-component (case (:authentication config)
                          :shibboleth (shibboleth/login-component)
                          :oidc (oidc/login-component)
                          :fake (oidc/login-component)
                          nil)]
    [:<>
     (text :t.login/title)
     (text :t.login/text)
     (when login-component
       [:div.login-component.w-100.mt-4
        login-component])
     (text :t.login/alternative-text)
     (when-let [alternative-endpoint (:alternative-login-url config)]
       [:div.text-center.w-100.mt-4
        [atoms/link nil
         (nav/url-dest alternative-endpoint)
         (text :t.login/alternative)]])]))

(defn guide []
  [:div
   (component-info shibboleth/login-component)
   (example "shibboleth login" [shibboleth/login-component])
   (component-info oidc/login-component)
   (example "oidc login" [oidc/login-component])])
