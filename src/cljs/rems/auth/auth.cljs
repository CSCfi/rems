(ns rems.auth.auth
  (:require [rems.atoms :as atoms]
            [rems.auth.fake :as fake]
            [rems.auth.oidc :as oidc]
            [rems.globals]
            [rems.guide-util :refer [component-info example]]
            [rems.text :refer [text]]))

(defn login-component []
  (let [login-component (case (:authentication @rems.globals/config)
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
     (when-let [alternative-endpoint (:alternative-login-url @rems.globals/config)]
       [:div.text-center.w-100.mt-4
        [atoms/link nil
         alternative-endpoint
         (text :t.login/alternative)]])]))

(defn guide []
  [:div
   (component-info oidc/login-component)
   (example "oidc login" [oidc/login-component])])
