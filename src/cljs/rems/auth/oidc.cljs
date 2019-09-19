(ns rems.auth.oidc
  (:require [rems.atoms :as atoms :refer [document-title]]
            [rems.navbar :as nav]
            [rems.text :refer [text]]))

(defn login-component []
  [:div
   [document-title (text :t.login/title)]
   [:p (text :t.login/auth0-text)]
   [:div
    [atoms/link nil
     (nav/url-dest "/oidc-login")
     [atoms/image {:class "login-btn" :alt "OIDC"} "/img/oidc-logo.jpg"]]]])
