(ns rems.auth.auth
  (:require [re-frame.core :as rf]
            [rems.atoms :as atoms]
            [rems.auth.fake :as fake]
            [rems.auth.oidc :as oidc]
            [rems.guide-util :refer [component-info example]]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  (let [config @(rf/subscribe [:rems.config/config])
        login-component (case (:authentication config)
                          :oidc (oidc/login-component)
                          :fake (fake/login-component)
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
         alternative-endpoint
         (text :t.login/alternative)]])]))

(defn guide []
  [:div
   (component-info oidc/login-component)
   (example "oidc login" [oidc/login-component])])
