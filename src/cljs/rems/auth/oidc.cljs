(ns rems.auth.oidc
  (:require [rems.atoms :as atoms]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  [:div
   (text :t.login/oidc-title)
   (text :t.login/oidc-text)
   [:div.text-center
    [atoms/link {:class "btn btn-primary btn-lg login-btn"}
     "/oidc-login"
     (text :t.login/login)]]])
